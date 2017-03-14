package reldat;

import java.net.DatagramSocket;

public class ReldatConnection
{
	private int maxWindowSize;
	private String dstIPAddress;
	private int port;
	private DatagramSocket inSocket;
	private DatagramSocket outSocket;

	public ReldatConnection( int maxWindowSize )
	{
		this.maxWindowSize = maxWindowSize;
	}
	
	public void connect( String dstIPAddress, int port )
	{
		// TODO
	}
	
	public void send( String data )
	{
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
