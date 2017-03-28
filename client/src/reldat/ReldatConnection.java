package reldat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
	private DatagramSocket outSocket;
	private DatagramSocket inSocket;
	private int current_seq;
	
	private ArrayList<ReldatPacket> packetsSent = new ArrayList<>();  
	private ArrayList<Integer> seqsSent = new ArrayList<>();  
	
	public ReldatConnection( int maxWindowSize ) {
		this.srcMaxWindowSize = maxWindowSize;
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
        } 	catch( SocketException e ) {
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
	public void send(String data) throws IOException {
		/*ReldatPacket pkt = new ReldatPacket(data, ReldatHeader.MUDA, this.current_seq++, 0);
    	DatagramPacket dgPkt = pkt.toDatagramPacket( this.dstIPAddress, this.port );
    	this.outSocket.send(dgPkt);*/
		
		ReldatPacket[] pktsToSend = null;
		try {
			pktsToSend = packetize(data);
			System.out.println(pktsToSend);
			
			boolean term = false;
			int sendBase = 0;
			ArrayList<ReldatPacket> unAcked = new ArrayList<>();
			while (!term) {
				if (pktsToSend.length > 0) {
					//send window of packets
					for (int i = sendBase; i < pktsToSend.length && i < sendBase + this.dstMaxWindowSize; i++) {
						ReldatPacket pkt = pktsToSend[i];
						if (!unAcked.contains(pkt) && pkt != null) {
							DatagramPacket dgPkt = pkt.toDatagramPacket(this.dstIPAddress, this.port);
							this.outSocket.send(dgPkt);
							unAcked.add(pkt);
							this.timers.put(pkt, new Date().getTime());
						}
					}
				} else {
					
				}
				
				if (unAcked.size() > 0) {
					for (ReldatPacket currPkt : unAcked) {
						Date currentTime = new Date();
						if (((currentTime.getTime() - this.timers.get(currPkt))/(1000)) > 1) {
							//If timeout on packet, retransmit packet
							System.out.println("RETRANSMITTING PACKET#: " + currPkt.getHeader().getSequenceNumber());
							currPkt.addFlag(ReldatHeader.RETRANSMIT_FLAG);
							DatagramPacket dgPkt = currPkt.toDatagramPacket(this.dstIPAddress, this.port);
							this.outSocket.send(dgPkt);
							this.timers.put(currPkt, new Date().getTime());
						}
					}
					
					
					byte[] buffer = new byte[1000];
					DatagramPacket p = new DatagramPacket(buffer, buffer.length);
					try {
						this.inSocket.receive(p);
						ReldatPacket receivedPacket = ReldatPacket.bytesToPacket(p.getData());
						//If ACK received and ACK is for smallest unacked pkt, increment sendbase to next unacked sequence number
						if (this.seqsSent.contains(receivedPacket.getHeader().getAcknowledgementNumber())) {
							if (receivedPacket.getHeader().getAcknowledgementNumber() == this.seqsSent.get(0)) {
								sendBase++;
								unAcked.remove(0);
							}
							this.seqsSent.remove(0);
							System.out.println("ACK" + receivedPacket.getHeader().getAcknowledgementNumber());
						} else {
							
						}
					} catch (SocketTimeoutException e) {
						System.out.println("Timeout lol");
					}
					
				} else {
					term = true;	
				}
			}

			
		} catch (HeaderCorruptedException | PayloadCorruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public String recv() throws HeaderCorruptedException, PayloadCorruptedException {
		byte[] buffer = new byte[1000];
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		try {
			this.inSocket.receive(p);
			ReldatPacket receivedPacket = ReldatPacket.bytesToPacket(p.getData());
		} catch (IOException e) {
			e.printStackTrace();
		}
		// TODO
		return null;
	}
	
	public ReldatPacket[] packetize(String message) throws HeaderCorruptedException, PayloadCorruptedException {
		ReldatPacket[] pkts = new ReldatPacket[message.length()];
		System.out.println(pkts.length);
		int lastPacketNum = (int) (Math.ceil(message.length() / (float) ReldatPacket.PACKET_PAYLOAD_SIZE));
		int currentPacketNum = 0;
		
		System.out.println("" + currentPacketNum + "/" + lastPacketNum);
		
		while (currentPacketNum < lastPacketNum) {
			int startInd = currentPacketNum * ReldatPacket.PACKET_PAYLOAD_SIZE;
			int endInd = (currentPacketNum + 1) * ReldatPacket.PACKET_PAYLOAD_SIZE;
			String sub = (endInd > message.length()) ? message.substring(startInd) : message.substring(startInd, endInd);
		
			ReldatPacket newPkt = new ReldatPacket(sub, ReldatHeader.DATA_FLAG, this.getCurrentSequenceNumber(), 0);
			this.packetsSent.add(newPkt);
			
			pkts[currentPacketNum] = newPkt;
			currentPacketNum++;
		}
		
		pkts[lastPacketNum - 1].addFlag(ReldatHeader.EOD_FLAG);
		return pkts;
	}
	
	public int getCurrentSequenceNumber() {
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
        
        System.out.println( "Connection terminated." );
	}
}
