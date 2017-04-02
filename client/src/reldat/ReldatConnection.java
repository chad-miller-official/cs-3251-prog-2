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
	private ReldatPacket[] receiveBuffer;
	private ArrayList<ReldatPacket> packetsSent = new ArrayList<>();  
	private ArrayList<Integer> seqsSent = new ArrayList<>(); 
	
	private final int MAX_RETRANSMISSION_NO = 3;
	private final int PACKETTIMEOUT = 1; //SECONDS
	
	private String ret = "";
	private int bufferIndex = 0;
	private int sendBase = 0;
	
	public ReldatConnection( int maxWindowSize ) {
		this.srcMaxWindowSize = maxWindowSize;
		this.receiveBuffer = new ReldatPacket[maxWindowSize];
		this.current_seq = 0;
	}

	/*
	 * Three-Way Handshake.
	 * 
	 * 1. Client -> Server
	 *      FLAGS:   OPEN
	 *      PAYLOAD: Client's max window size
	 * 
	 * 2. Server -> Client
	 *      FLAGS:   OPEN | ACK
	 *      PAYLOAD: Server's max window size
	 * 
	 * 3. Client -> Server
	 *      FLAGS:   ACK
	 *      PAYLOAD: Nothing
	 */
	public boolean connect( String dstIPAddress, int port ) throws IOException {
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

        ReldatPacket syn = new ReldatPacket( srcMaxWindowSize, ReldatHeader.OPEN_FLAG, this.getCurrentSequenceNumber(), 0 );

        byte[] buffer       = new byte[1000];
        DatagramPacket pkt  = new DatagramPacket( buffer, buffer.length );
        ReldatPacket synAck = null;
        int resends         = 0;
        
        do {
            try {
            	// Step 1: Send initial SYN packet to server
            	DatagramPacket packet = syn.toDatagramPacket( this.dstIPAddress, this.port );
                this.outSocket.send( packet );
                System.out.println( "Sent SYN (packet 1/3)." );

                // Step 2: Receive SYNACK from server
				this.inSocket.receive( pkt );
	    		synAck = ReldatPacket.bytesToPacket( pkt.getData() );
			} catch(SocketTimeoutException e) {
				System.out.println("Server did not respond - retrying...");
			} catch(HeaderCorruptedException | PayloadCorruptedException e) {
            	System.out.println("Server replied with corrupted packet - retrying...");
            	resends--; // Don't count a corrupted packet as a non-response
            }

            resends++;
            System.out.println(resends + ":" + MAX_RETRANSMISSION_NO);

            if (resends >= MAX_RETRANSMISSION_NO)
            	break;
        } while(synAck == null || !(synAck.isACK() && synAck.isOpen()));

        if (resends >= MAX_RETRANSMISSION_NO) {
        	System.out.println("Server unreachable.");
        	return false;
        }

        this.dstMaxWindowSize = Integer.parseInt( new String( synAck.getData() ) );
        System.out.println( "Received SYNACK (packet 2/3)." );

    	// Step 3: Send ACK to server
    	ReldatPacket ack         = new ReldatPacket( "", ReldatHeader.ACK_FLAG, 0, synAck.getHeader().getSequenceNumber() );
    	DatagramPacket ackPacket = ack.toDatagramPacket( this.dstIPAddress, this.port );
    	this.outSocket.send( ackPacket );
    	System.out.println( "Sent ACK (packet 3/3).");

        System.out.println( "Connection established." );
        return true;
	}
	
	//pipelined
	public String conversation(String data) throws IOException {		
		int counter = 1;
		
		try {
			ReldatPacket[] pktsToSend = packetize(data);
			boolean eodSent = false;

			System.out.println(Arrays.toString(pktsToSend));

			boolean conversationOver = false;

			while (!conversationOver) {
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
								System.out.println("Max Retransmissions allowed for " + currPkt.getHeader().getSequenceNumber() + ". Assuming server failure.");
								return null;
							}
						}
					}
				} else if (!eodSent) {
					ReldatPacket eod = new ReldatPacket("", ReldatHeader.EOD_FLAG, getCurrentSequenceNumber(), 0);
					sendData(eod, false);	
					eodSent = true;
				}
				
				counter += 1;
				
				if( counter == 4000000 )
				{
					try {
						Thread.sleep(15000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				
				conversationOver = listen();
			}
		} catch (HeaderCorruptedException | PayloadCorruptedException e) {
			e.printStackTrace();
		}
		
		byte[] buffer = new byte[1000];
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);

		try {
			this.inSocket.receive(p);
			ReldatPacket receivedPacket = ReldatPacket.bytesToPacket(p.getData());

			if (receivedPacket.isData()) {
				System.out.println("Received packet " + receivedPacket.getHeader().getSequenceNumber() + ": " + new String(receivedPacket.getData()));
				
				if(bufferFull()) {
					String bufferContents = "";
					System.out.println("Flushing buffer...");
	
					for(ReldatPacket rp : receiveBuffer)
						bufferContents += new String(rp.getData());
					
					this.receiveBuffer = new ReldatPacket[this.srcMaxWindowSize];
					this.ret += bufferContents;
					this.bufferIndex += this.srcMaxWindowSize;
				}
				
				int index = receivedPacket.getHeader().getSequenceNumber() - this.bufferIndex;
	
				if(!receivedPacket.isRetransmit() || receiveBuffer[index] == null)
					receiveBuffer[index] = receivedPacket;
				
				this.sendACK(receivedPacket, false);
			}
		} catch (SocketTimeoutException | HeaderCorruptedException | PayloadCorruptedException e) {
			// TODO Auto-generated catch block
		}

		String bufferContents = "";
		System.out.println("Flushing buffer...");

		for(ReldatPacket rp : receiveBuffer)
			if(rp != null)
			bufferContents += new String(rp.getData());
		
		this.receiveBuffer = new ReldatPacket[this.srcMaxWindowSize];
		ret += bufferContents;

		return ret;
	}
	
	public boolean listen()
	{
		byte[] buffer = new byte[1000];
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);

		try {
			this.inSocket.receive(p);
			ReldatPacket receivedPacket = ReldatPacket.bytesToPacket(p.getData());
		
			if(receivedPacket.isACK()) {
				if (this.unAcked.contains(receivedPacket)) {
					if(receivedPacket.isEOD()) {
						System.out.println("EOD ACK RECEIVED FROM SERVER");
					}
					else if (receivedPacket.getHeader().getAcknowledgementNumber() == unAcked.get(0).getHeader().getSequenceNumber()) {
						//If ACK received and ACK is for smallest unacked pkt, increment sendbase to next unacked sequence number
						this.sendBase++;
					}

					this.unAcked.remove(receivedPacket);
					this.seqsSent.remove((Object)receivedPacket.getHeader().getSequenceNumber());
					System.out.println(this.seqsSent);
					System.out.println("ACK" + receivedPacket.getHeader().getAcknowledgementNumber());
				}
			} else if (receivedPacket.isData()) {
				System.out.println("Received packet " + receivedPacket.getHeader().getSequenceNumber() + ": " + new String(receivedPacket.getData()));
				
				if(bufferFull()) {
					String bufferContents = "";
					System.out.println("Flushing buffer...");

					for(ReldatPacket rp : receiveBuffer)
						bufferContents += new String(rp.getData());
					
					this.receiveBuffer = new ReldatPacket[this.srcMaxWindowSize];
					this.ret += bufferContents;
					this.bufferIndex += this.srcMaxWindowSize;
				}
				
				int index = receivedPacket.getHeader().getSequenceNumber() - this.bufferIndex;

				if(!receivedPacket.isRetransmit() || receiveBuffer[index] == null)
					receiveBuffer[index] = receivedPacket;
				
				this.sendACK(receivedPacket, false);
			} else if (receivedPacket.isEOD()) {
				System.out.println("RECEIVED EOD");
				this.sendACK(receivedPacket, true);
				return true;
			} else if (receivedPacket.isNudge()) {
				ReldatPacket nudge = new ReldatPacket( "", (byte)(ReldatHeader.NUDGE_FLAG | ReldatHeader.ACK_FLAG), 0, 0 );
				DatagramPacket nudgePkt = nudge.toDatagramPacket(this.dstIPAddress, this.port);
				this.outSocket.send(nudgePkt);
			}
		} catch (SocketTimeoutException e) {
			return false;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (HeaderCorruptedException | PayloadCorruptedException e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	private void sendACK(ReldatPacket pkt, boolean isEOD)
	{
		System.out.println("Sending ACK for " + pkt.getHeader().getSequenceNumber());

		byte flags = ReldatHeader.ACK_FLAG;
		
		if( isEOD )
			flags |= ReldatHeader.EOD_FLAG;

		try {
			ReldatPacket ack = new ReldatPacket("", flags, 0, pkt.getHeader().getSequenceNumber());
			DatagramPacket ackPkt = ack.toDatagramPacket(this.dstIPAddress, this.port);
			this.outSocket.send(ackPkt);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private boolean bufferFull()
	{
		for(int i = 0; i < receiveBuffer.length; i++) {
			if(receiveBuffer[i] == null)
				return false;
		}
		
		return true;
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
	 *      PAYLOAD: Nothing
	 * 
	 * 2. Server -> Client
	 *      FLAGS:   CLOSE | ACK
	 *      PAYLOAD: Nothing
	 * 
	 * 3. Server -> Client
	 *      FLAGS:   CLOSE
	 *      PAYLOAD: Nothing
	 * 
	 * 4. Client -> Server
	 *      FLAGS:   CLOSE | ACK
	 *      PAYLOAD: Nothing
	 */
	public boolean disconnect() throws IOException
	{
		System.out.println( "Attempting to disconnect from " + this.dstIPAddress + ":" + this.port + "..." );
		
        byte[] buffer               = new byte[1000];
        DatagramPacket closeAck1Pkt = new DatagramPacket( buffer, buffer.length );
		ReldatPacket closeAck1      = null;
		int resends                 = 0;

		do {
			try {
				// Step 1. Send client-side CLOSE to server
				ReldatPacket clientClose         = new ReldatPacket( "", ReldatHeader.CLOSE_FLAG, this.getCurrentSequenceNumber(), 0 );
				DatagramPacket clientClosePacket = clientClose.toDatagramPacket( this.dstIPAddress, this.port );
				this.outSocket.send( clientClosePacket );
				
				System.out.println( "Sent client-side CLOSE (packet 1/4)." );
				
				// Step 2. Receive server-side CLOSEACK from server
	            this.inSocket.receive( closeAck1Pkt );
	            closeAck1 = ReldatPacket.bytesToPacket( closeAck1Pkt.getData() );
			} catch(SocketTimeoutException e) {
				System.out.println("Server did not respond - retrying...");
			} catch(HeaderCorruptedException | PayloadCorruptedException e) {
            	System.out.println("Server replied with corrupted packet - retrying...");
            	resends--; // Don't count a corrupted packet as a non-response
            }

            resends++;
            System.out.println(resends + ":" + MAX_RETRANSMISSION_NO);

            if (resends >= MAX_RETRANSMISSION_NO)
            	break;
		} while (closeAck1 == null || !(closeAck1.isClose() && closeAck1.isACK()));
		
        if (resends >= MAX_RETRANSMISSION_NO) {
        	System.out.println("Server did not respond. Assuming server failure.");
        	return false;
        }

		System.out.println( "Received CLOSEACK (packet 2/4)" );

		buffer                        = new byte[1000];
		DatagramPacket serverClosePkt = new DatagramPacket( buffer, buffer.length );
		ReldatPacket serverClose      = null;
		resends                       = 0;

		do {
			try {
				// Step 3. Receive server-side CLOSE from server
				this.inSocket.receive( serverClosePkt );
				serverClose = ReldatPacket.bytesToPacket(serverClosePkt.getData());
			} catch(SocketTimeoutException e) {
				System.out.println( "Timeout" );
			} catch(HeaderCorruptedException | PayloadCorruptedException e) {
				System.out.println("Server replied with corrupted packet - retrying..");
				resends--;
			}
			
			resends++;
			
			if (resends >= MAX_RETRANSMISSION_NO)
				break;
		} while (serverClose == null || !serverClose.isClose());
		
        if (resends >= MAX_RETRANSMISSION_NO) {
        	System.out.println("Server did not respond. Assuming server failure.");
        	return false;
        }
		
		System.out.println( "Received server-side CLOSE (packet 3/4)" );
		
		// Step 4. Send client-side ACK to server
		ReldatPacket closeAck2         = new ReldatPacket( "", (byte)(ReldatHeader.CLOSE_FLAG | ReldatHeader.ACK_FLAG), 0, serverClose.getHeader().getSequenceNumber() );
		DatagramPacket closeAck2Packet = closeAck2.toDatagramPacket( this.dstIPAddress, this.port );
		this.outSocket.send( closeAck2Packet );
		
		System.out.println( "Sent CLOSEACK (packet 4/4)" );
        
		this.outSocket.close();
		this.inSocket.close();

        System.out.println( "Connection terminated." );
        return true;
	}
}
