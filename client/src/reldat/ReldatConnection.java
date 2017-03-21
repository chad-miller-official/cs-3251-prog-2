package reldat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

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
            ReldatPacket syn      = new ReldatPacket( maxWindowSize, ReldatHeader.OPEN_FLAG, 0, 0);
            DatagramPacket packet = syn.toDatagramPacket( this.dstIPAddress, this.port );

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
