public class Reldat
{
	private int maxWindowSize;
	
	private String dstIPAddress;
	private int port;
	// TODO inSocket
	// TODO outSocket

	public Reldat( int maxWindowSize )
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
