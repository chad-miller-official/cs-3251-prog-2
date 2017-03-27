package reldat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import java.lang.Math; 
import java.util.ArrayList;

import reldat.exception.HeaderCorruptedException;
import reldat.exception.PayloadCorruptedException;

public class ReldatConnection {
	private int srcMaxWindowSize, dstMaxWindowSize;
	private InetAddress dstIPAddress;
	private int port;

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
	 *      PAYLOAD: ~NOAM
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
            this.inSocket = new DatagramSocket();
        }
        catch( SocketException e ) {
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
            this.outSocket.receive( pkt );
                            
        	ReldatPacket synAck   = ReldatPacket.bytesToPacket( pkt.getData() );
        	this.dstMaxWindowSize = Integer.parseInt( new String( synAck.getData() ) );
        	
        	System.out.println( "Received SYNACK (packet 2/3)." );

        	// Step 3: Send ACK to server
        	ReldatPacket ack         = new ReldatPacket( "~NOAM", ReldatHeader.ACK_FLAG, 2, 1 );
        	DatagramPacket ackPacket = ack.toDatagramPacket( this.dstIPAddress, this.port );
        	this.outSocket.send( ackPacket );
        	
        	System.out.println( "Sent ACK (packet 3/3).");
    	} catch(IOException | HeaderCorruptedException | PayloadCorruptedException e) {
            e.printStackTrace();
        }
        System.out.println( "Connection established." );
	}

	public void send(String data) throws IOException {
		//TODO: break message into packets
		/*ReldatPacket pkt = new ReldatPacket(data, ReldatHeader.MUDA, this.current_seq++, 0);
    	DatagramPacket dgPkt = pkt.toDatagramPacket( this.dstIPAddress, this.port );
    	this.outSocket.send(dgPkt);*/
		
		ReldatPacket[] pktsToSend = packetize(data);
		for (int i = 0; i < pktsToSend.length; i++) {
			ReldatPacket pkt = pktsToSend[i];
			if (pkt != null) {
				DatagramPacket dgPkt = pkt.toDatagramPacket(this.dstIPAddress, this.port);
				this.outSocket.send(dgPkt);
			}
		}
	}

	public String recv() throws HeaderCorruptedException, PayloadCorruptedException {
		byte[] buffer = new byte[1000];
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		try {
			this.inSocket.receive(p);
			ReldatPacket receivedPacket = ReldatPacket.bytesToPacket(p.getData());
			byte[] rec = receivedPacket.toBytes();
			
			for (int i = 0; i < rec.length; i++) {
				System.out.println(rec[i]);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		// TODO
		return null;
	}
	
	public ReldatPacket[] packetize(String message) {
		ReldatPacket[] pkts = new ReldatPacket[this.dstMaxWindowSize];
		System.out.println(pkts.length);
		int lastPacketNum = (int) (Math.ceil(message.length() / (float) ReldatPacket.PACKET_PAYLOAD_SIZE));
		int currentPacketNum = 0;
		
		System.out.println("" + currentPacketNum + "/" + lastPacketNum);
		
		while (currentPacketNum < lastPacketNum) {
			int startInd = currentPacketNum * ReldatPacket.PACKET_PAYLOAD_SIZE;
			int endInd = (currentPacketNum + 1) * ReldatPacket.PACKET_PAYLOAD_SIZE;
			String sub = (endInd > message.length()) ? message.substring(startInd) : message.substring(startInd, endInd);
		
			ReldatPacket newPkt = new ReldatPacket(sub, ReldatHeader.MUDA, this.getCurrentSequenceNumber(), 0);
			this.packetsSent.add(newPkt);
			
			pkts[currentPacketNum] = newPkt;
			currentPacketNum++;
		}
		
		return pkts; 
	}
	
	public int getCurrentSequenceNumber() {
		this.seqsSent.add(this.current_seq);
		this.current_seq++;
		/*ReldatPacket curr = this.packetsSent.get(this.packetsSent.size() - 1);
		return curr.getHeader().getSequenceNumber();*/
		return this.seqsSent.get(this.seqsSent.size() - 1);
	}
	

	public void disconnect() {
		// TODO
	}
}
