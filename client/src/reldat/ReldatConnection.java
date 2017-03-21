package reldat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import reldat.exception.HeaderCorruptedException;
import reldat.exception.PayloadCorruptedException;

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
		System.out.println( dstIPAddress );
		System.out.println( port );

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
                System.out.println( "Sent a packet" );

                boolean kappa = false;
                byte[] buffer = new byte[1000];
                DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                this.outSocket.receive(pkt);

                try {
                    System.out.println(ReldatPacket.bytesToPacket(pkt.getData()));
                } catch (HeaderCorruptedException e) {
                    e.printStackTrace();
                } catch (PayloadCorruptedException e) {
                    e.printStackTrace();
                }

            } catch (IOException e) {
                e.printStackTrace();
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
