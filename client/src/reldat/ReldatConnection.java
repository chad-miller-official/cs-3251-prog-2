package reldat;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import java.lang.Math; 
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Date;

import reldat.exception.HeaderCorruptedException;
import reldat.exception.PayloadCorruptedException;

public class ReldatConnection {
	private int srcMaxWindowSize, dstMaxWindowSize;
	private InetAddress dstIPAddress;
	private int port;
	private HashMap<ReldatPacket, Long> timers = new HashMap<>();
	private HashMap<Integer, Integer> retransmissions = new HashMap<>(); //maps seq nums to retransmission count
	private ArrayList<ReldatPacket> unAcked = new ArrayList<>();
	private DatagramSocket outSocket;
	private DatagramSocket inSocket;
	private int current_seq;
	private ArrayList<ReldatPacket> receiveBuffer;
	private ArrayList<ReldatPacket> packetsSent = new ArrayList<>();  
	private ArrayList<Integer> seqsSent = new ArrayList<>(); 
	
	private final int MAX_RETRANSMISSION_NO = 3;
	private final int PACKETTIMEOUT = 1; //SECONDS
	
	public ReldatConnection( int maxWindowSize ) {
		this.srcMaxWindowSize = maxWindowSize;
		this.receiveBuffer = new ArrayList<>();
		this.current_seq = 3;
	}

	/*
	 * Three-Way Handshake.
	 * 
	 * 1. Client -> Server
	 *      FLAGS:   OPEN
	 *      SEQ:     0
	 *      ACK:     0
	 *      PAYLOAD: Client's max window size
	 * 
	 * 2. Server -> Client
	 *      FLAGS:   OPEN | ACK
	 *      SEQ:     1
	 *      ACK:     0
	 *      PAYLOAD: Server's max window size
	 * 
	 * 3. Client -> Server
	 *      FLAGS:   ACK
	 *      SEQ:     2
	 *      ACK:     1
	 *      PAYLOAD: Epsilon
	 */
	public void connect( String dstIPAddress, int port ) {
		try {
			this.dstIPAddress = InetAddress.getByName( dstIPAddress );
			this.port = port;
		} catch( UnknownHostException e ) {
			e.printStackTrace();
		}
		
		System.out.println( "Attempting to connect to " + dstIPAddress + ":" + port + "..." );

        try {
            this.outSocket = new DatagramSocket();	
            this.inSocket = new DatagramSocket(this.port + 1); // XXX DEBUG TODO REMOVE
            this.inSocket.setSoTimeout(1000);
        } catch(SocketException e) {
        	e.printStackTrace();
        }

        try {
        	// Step 1: Send initial SYN packet to server
            ReldatPacket syn      = new ReldatPacket( srcMaxWindowSize, ReldatHeader.OPEN_FLAG, 0, 0 );
            DatagramPacket packet = syn.toDatagramPacket( this.dstIPAddress, this.port );
            this.outSocket.send( packet );

            System.out.println( "Sent SYN (packet 1/3)." );

            // Step 2: Receive SYNACK from server
            byte[] buffer      = new byte[1000];
            DatagramPacket pkt = new DatagramPacket( buffer, buffer.length );
            this.inSocket.receive( pkt );
                            
        	ReldatPacket synAck   = ReldatPacket.bytesToPacket( pkt.getData() );
        	this.dstMaxWindowSize = Integer.parseInt( new String( synAck.getData() ) );
        	
        	System.out.println( "Received SYNACK (packet 2/3)." );

        	// Step 3: Send ACK to server
        	ReldatPacket ack         = new ReldatPacket( "", ReldatHeader.ACK_FLAG, 2, 1 );
        	DatagramPacket ackPacket = ack.toDatagramPacket( this.dstIPAddress, this.port );
        	this.outSocket.send( ackPacket );
        	
        	System.out.println( "Sent ACK (packet 3/3).");
    	} catch(IOException | HeaderCorruptedException | PayloadCorruptedException e) {
            e.printStackTrace();
        }
        System.out.println( "Connection established." );
	}
	
	//pipelined
	public String conversation(String data) throws IOException {		
		String ret = "";
		
		try {
			ReldatPacket[] pktsToSend = packetize(data);
			int sendBase = 0;
			boolean eodSent = false, eodAcked = false;

			System.out.println(Arrays.toString(pktsToSend));

			while (true) {
				if (sendBase < pktsToSend.length) {
					//send window of packets
					String sentWindow = "WINDOW SENT: ";

					for (int i = sendBase; i < pktsToSend.length && i < sendBase + this.dstMaxWindowSize; i++) {
						ReldatPacket pkt = pktsToSend[i];
						
						if (!unAcked.contains(pkt) && pkt != null) {
							sendData(pkt, false);
							sentWindow += pkt.getHeader().getSequenceNumber() + ", ";
						} else {
							sentWindow += "null, ";
						}
					}

					System.out.println(sentWindow);
				}
				
				if (unAcked.size() > 0) {
					for (ReldatPacket currPkt : unAcked) {
						//If timeout on packet, retransmit packet
						if (((new Date().getTime() - this.timers.get(currPkt)) / 1000) > this.PACKETTIMEOUT) {
							if (this.retransmissions.get(currPkt.getHeader().getSequenceNumber()) < this.MAX_RETRANSMISSION_NO) {
								System.out.println("RETRANSMITTING PACKET # " + currPkt.getHeader().getSequenceNumber() + " for the " + this.retransmissions.get(currPkt.getHeader().getSequenceNumber()) + " time.");
								sendData(currPkt, true);
							} else {
								//System.out.println("Max Retransmissions allowed for " + currPkt.getHeader().getSequenceNumber());
							}
						}
					}
				} else if (!eodSent) {
					ReldatPacket eod = new ReldatPacket("", ReldatHeader.EOD_FLAG, getCurrentSequenceNumber(), 0);
					sendData(eod, false);	
					eodSent = true;
				} else if (eodAcked) {
					break;
				}
				
				byte[] buffer = new byte[1000];
				DatagramPacket p = new DatagramPacket(buffer, buffer.length);

				try {
					this.inSocket.receive(p);
					ReldatPacket receivedPacket = ReldatPacket.bytesToPacket(p.getData());

					System.out.println(receivedPacket.getPayload());
				
					if(receivedPacket.isACK()) {
						if (this.seqsSent.contains(receivedPacket.getHeader().getAcknowledgementNumber())) {
							if(receivedPacket.isEOD()) {
								eodAcked = true;
								System.out.println("EOD RECEIVED FROM SERVER");
							}
							else if (receivedPacket.getHeader().getAcknowledgementNumber() == unAcked.get(0).getHeader().getSequenceNumber()) {
								//If ACK received and ACK is for smallest unacked pkt, increment sendbase to next unacked sequence number
								sendBase++;
								this.receiveBuffer.add(receivedPacket);
							}

							this.unAcked.remove(0);
							this.seqsSent.remove(0);
							System.out.println(this.seqsSent);
							System.out.println("ACK" + receivedPacket.getHeader().getAcknowledgementNumber());
						}
					}
					
					String buf = "[";

					for (ReldatPacket x : this.receiveBuffer) {
						buf += x.getPayload() + ",";
					}

					System.out.println(buf + "]");

					if (!receivedPacket.isRetransmit()) {
						for (ReldatPacket pkt : this.receiveBuffer) {
							ret += pkt.getPayload();
						}

						this.receiveBuffer.clear();
					}
				} catch (SocketTimeoutException e) {
					System.out.println("Timeout lol");
				}
			}
		} catch (HeaderCorruptedException | PayloadCorruptedException e) {
			e.printStackTrace();
		}

		return ret;
	}
	
	private void sendData(ReldatPacket pkt, boolean isRetransmission) throws IOException {
		if (isRetransmission)
			pkt.addFlag(ReldatHeader.RETRANSMIT_FLAG);

		DatagramPacket dgPkt = pkt.toDatagramPacket(this.dstIPAddress, this.port);
		this.outSocket.send(dgPkt);
		this.timers.put(pkt, new Date().getTime());
				
		if (!isRetransmission) {
			this.retransmissions.put(pkt.getHeader().getSequenceNumber(), 0);
			this.unAcked.add(pkt);
		}
		else
			this.retransmissions.put(pkt.getHeader().getSequenceNumber(), this.retransmissions.get(pkt.getHeader().getSequenceNumber()) + 1);
	}
	
	private ReldatPacket[] packetize(String message) throws HeaderCorruptedException, PayloadCorruptedException {
		int lastPacketNum = (int) (Math.ceil(message.length() / (float) ReldatPacket.PACKET_PAYLOAD_SIZE));

		ReldatPacket[] pkts = new ReldatPacket[lastPacketNum];
		System.out.println(pkts.length);
		int currentPacketNum = 0;
		
		System.out.println("" + currentPacketNum + "/" + lastPacketNum);
		
		while (currentPacketNum < lastPacketNum) {
			int startInd = currentPacketNum * ReldatPacket.PACKET_PAYLOAD_SIZE;
			int endInd = (currentPacketNum + 1) * ReldatPacket.PACKET_PAYLOAD_SIZE;
			String sub = (endInd > message.length()) ? message.substring(startInd) : message.substring(startInd, endInd);
		
			ReldatPacket newPkt = null;
			try {
				newPkt = new ReldatPacket(sub, ReldatHeader.DATA_FLAG, this.getCurrentSequenceNumber(), 0);
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			this.packetsSent.add(newPkt);
			
			pkts[currentPacketNum] = newPkt;
			currentPacketNum++;
		}
		
		return pkts;
	}
	
	private int getCurrentSequenceNumber() {
		this.seqsSent.add(this.current_seq);
		this.current_seq++;
		/*ReldatPacket curr = this.packetsSent.get(this.packetsSent.size() - 1);
		return curr.getHeader().getSequenceNumber();*/
		return this.seqsSent.get(this.seqsSent.size() - 1);
	}

	/*
	 * Four-Way Handshake.
	 * 
	 * 1. Client -> Server
	 *      FLAGS:   CLOSE
	 *      SEQ:     0
	 *      ACK:     0
	 *      PAYLOAD: Nothing
	 * 
	 * 2. Server -> Client
	 *      FLAGS:   CLOSE | ACK
	 *      SEQ:     1
	 *      ACK:     0
	 *      PAYLOAD: Nothing
	 * 
	 * 3. Server -> Client
	 *      FLAGS:   CLOSE
	 *      SEQ:     2
	 *      ACK:     0
	 *      PAYLOAD: Nothing
	 * 
	 * 4. Client -> Server
	 *      FLAGS:   CLOSE | ACK
	 *      SEQ:     3
	 *      ACK:     2
	 *      PAYLOAD: Nothing
	 */
	public void disconnect()
	{
		System.out.println( "Attempting to disconnect from " + this.dstIPAddress + ":" + this.port + "..." );
		
		try
		{
			// Step 1. Send client-side CLOSE to server
			ReldatPacket clientClose         = new ReldatPacket( "", ReldatHeader.CLOSE_FLAG, 0, 0 );
			DatagramPacket clientClosePacket = clientClose.toDatagramPacket( this.dstIPAddress, this.port );
			this.outSocket.send( clientClosePacket );
			
			System.out.println( "Sent client-side CLOSE (packet 1/4)." );
			
			// Step 2. Receive server-side CLOSEACK from server
            byte[] buffer            = new byte[1000];
            DatagramPacket closeAck1 = new DatagramPacket( buffer, buffer.length );
            this.inSocket.receive( closeAck1 );
			
			System.out.println( "Received CLOSEACK (packet 2/4)" );
			
			// Step 3. Receive server-side CLOSE from server
			buffer                     = new byte[1000];
			DatagramPacket serverClose = new DatagramPacket( buffer, buffer.length );
			this.inSocket.receive( serverClose );
			
			System.out.println( "Received server-side CLOSE (packet 3/4)" );
			
			// Step 4. Send client-side CLOSEACK to server
			ReldatPacket closeAck2         = new ReldatPacket( "", ReldatHeader.CLOSE_FLAG, 2, 0 );
			DatagramPacket closeAck2Packet = closeAck2.toDatagramPacket( this.dstIPAddress, this.port );
			this.outSocket.send( closeAck2Packet );
			
			System.out.println( "Sent CLOSEACK (packet 4/4)" );
		}
		catch(SocketTimeoutException e)
		{
			System.out.println( "Timeout" );
		}
        catch( IOException e )
        {
            e.printStackTrace();
        }
        
		this.inSocket.close(); //TODO: REMOVE AFTER BUG THAT CAUSES SOCKET TO BE IN USE IS FIXED
        System.out.println( "Connection terminated." );
	}
}
