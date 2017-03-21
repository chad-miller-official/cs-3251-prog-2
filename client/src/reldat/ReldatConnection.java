package reldat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class ReldatConnection
{
	private int maxWindowSize;
	private InetAddress dstIPAddress;
	private int port;
	
	private DatagramSocket inSocket, outSocket;

	public ReldatConnection( int maxWindowSize ) {
		this.maxWindowSize = maxWindowSize;
	}

	public void connect( String dstIPAddress, int port ){
		try
		{
			this.dstIPAddress = InetAddress.getByName(dstIPAddress);
		}
		catch( UnknownHostException e )
		{
			e.printStackTrace();
		}
		
		this.port = port;

        try {
            this.outSocket = new DatagramSocket();
            this.inSocket  = new DatagramSocket();
        } catch (SocketException e) {
            System.out.println("Establishing connection failed.");
        }

        if (this.outSocket != null) {
        	byte[] windowSizeBytes = ByteBuffer.allocate( 4 ).putInt( maxWindowSize ).array();
            ReldatPacket syn = new ReldatPacket( windowSizeBytes, ReldatHeader.OPEN_FLAG, 0, 0);
            byte[] synBytes = syn.toBytes();
            DatagramPacket packet = new DatagramPacket(synBytes, synBytes.length, this.dstIPAddress, port);

            try {
                this.outSocket.send(packet);

                boolean kappa = false;
                while (!kappa) {

                }
            } catch (IOException e) {
                System.out.println("IO");
            }
        }
	}

	public void send( String data ) {
		// TODO
	}

	public String recv()
	{
		// TODO
		return null;
	}

	public void disconnect()
	{
		// TODO
	}
}
