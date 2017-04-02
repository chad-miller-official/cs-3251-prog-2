package reldat;

import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import reldat.exception.HeaderCorruptedException;
import reldat.exception.PayloadCorruptedException;

public class ReldatPacket {
	public static final short MAX_PACKET_SIZE     = 1000;
	public static final short PACKET_PAYLOAD_SIZE = MAX_PACKET_SIZE - ReldatHeader.PACKET_HEADER_SIZE;

	private ReldatHeader header;
	private byte[] headerChecksum;
	private byte[] data;

	public ReldatPacket( int data, byte flags, int seqNum, int ackNum ) throws UnsupportedEncodingException
	{
		this(Integer.toString( data ), flags, seqNum, ackNum );
	}

	public ReldatPacket( String data, byte flags, int seqNum, int ackNum ) throws UnsupportedEncodingException {
		this(data.getBytes("UTF-8"), flags, seqNum, ackNum );
	}

	public ReldatPacket( byte[] data, byte flags, int seqNum, int ackNum ) {
		this.data = data;
		
		if( this.data == null )
			this.data = new byte[]{};

		header = new ReldatHeader( flags, seqNum, ackNum, data );

		try
		{
			MessageDigest checksumGenerator = MessageDigest.getInstance( "MD5" );
			headerChecksum = checksumGenerator.digest( header.toBytes() );
		}
		catch( NoSuchAlgorithmException e )
		{
			e.printStackTrace();
		}
	}

	// Private constructor for bytesToPacket()
	private ReldatPacket( ReldatHeader header, byte[] headerChecksum, byte[] data )
	{
		this.header         = header;
		this.headerChecksum = headerChecksum;
		this.data           = data;
	}

	public ReldatHeader getHeader()
	{
		return header;
	}

	public byte[] getHeaderChecksum()
	{
		return headerChecksum;
	}

	public byte[] getData()
	{
		return this.data;
	}
	
	public String getPayload() throws UnsupportedEncodingException {
		return new String(this.data, "UTF-8");
	}
	
	public boolean isACK() {
		return ((this.getHeader().getFlags() & ReldatHeader.ACK_FLAG) == ReldatHeader.ACK_FLAG);
	}
	
	public boolean isRetransmit() {
		return ((this.getHeader().getFlags() & ReldatHeader.RETRANSMIT_FLAG) == ReldatHeader.RETRANSMIT_FLAG);
	}
	
	public boolean isEOD() {
		return (this.getHeader().getFlags() & ReldatHeader.EOD_FLAG) == ReldatHeader.EOD_FLAG;
	}
	
	public boolean isData() {
		return (this.getHeader().getFlags() & ReldatHeader.DATA_FLAG) == ReldatHeader.DATA_FLAG;
	}
	
	public boolean isOpen() {
		return (this.getHeader().getFlags() & ReldatHeader.OPEN_FLAG) == ReldatHeader.OPEN_FLAG;
	}
	
	public boolean isNudge() {
		return (this.getHeader().getFlags() & ReldatHeader.NUDGE_FLAG) == ReldatHeader.NUDGE_FLAG;
	}
	
	public boolean isClose() {
		return (this.getHeader().getFlags() & ReldatHeader.CLOSE_FLAG) == ReldatHeader.CLOSE_FLAG;
	}

	public byte[] toBytes()
	{
		byte[] headerBytes = header.toBytes();
		byte[] packetBytes = new byte[ headerBytes.length + headerChecksum.length + this.data.length ];

		System.arraycopy(
			headerBytes,
			0,
			packetBytes,
			0,
			headerBytes.length
		);

		System.arraycopy(
			headerChecksum,
			0,
			packetBytes,
			headerBytes.length,
			headerChecksum.length
		);

		System.arraycopy(
			this.data,
			0,
			packetBytes,
			headerBytes.length + headerChecksum.length,
			this.data.length
		);

		return packetBytes;
	}

	public DatagramPacket toDatagramPacket( InetAddress dstIPAddress, int port )
	{
		byte[] thisBytes = toBytes();
		return new DatagramPacket( thisBytes, thisBytes.length, dstIPAddress, port);
	}
	
	public void addFlag(byte flag)
	{
		this.header.addFlag(flag);

		try
		{
			MessageDigest checksumGenerator = MessageDigest.getInstance( "MD5" );
			headerChecksum = checksumGenerator.digest( header.toBytes() );
		}
		catch( NoSuchAlgorithmException e )
		{
			e.printStackTrace();
		}
	}

	public static ReldatPacket bytesToPacket( byte[] packetData ) throws HeaderCorruptedException, PayloadCorruptedException
	{
		byte[] headerBytes    = new byte[ReldatHeader.PACKET_HEADER_SIZE - 16];
		byte[] headerChecksum = new byte[16];

		System.arraycopy(
			packetData,
			0,
			headerBytes,
			0,
			ReldatHeader.PACKET_HEADER_SIZE - 16
		);

		System.arraycopy(
			packetData,
			ReldatHeader.PACKET_HEADER_SIZE - 16,
			headerChecksum,
			0,
			16
		);

		try
		{
			MessageDigest checksumGenerator = MessageDigest.getInstance( "MD5" );

			byte[] expectedHeaderChecksum = checksumGenerator.digest( headerBytes );
			checksumGenerator.reset();

			if( !Arrays.equals( headerChecksum, expectedHeaderChecksum ) )
				throw new HeaderCorruptedException();

			ReldatHeader header = ReldatHeader.bytesToHeader( headerBytes );
			int payloadSize     = header.getPayloadSize();
			byte[] payloadBytes = new byte[payloadSize];

			System.arraycopy(
				packetData,
				ReldatHeader.PACKET_HEADER_SIZE,
				payloadBytes,
				0,
				payloadSize
			);

			byte[] expectedPayloadChecksum = checksumGenerator.digest( payloadBytes );
			checksumGenerator.reset();

			if( !Arrays.equals( header.getPayloadChecksum(), expectedPayloadChecksum ) )
				throw new PayloadCorruptedException();

			return new ReldatPacket( header, headerChecksum, payloadBytes );
		}
		catch( NoSuchAlgorithmException e )
		{
			e.printStackTrace();
		}

		return null;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(data);
		result = prime * result + ((header == null) ? 0 : header.hashCode());
		result = prime * result + Arrays.hashCode(headerChecksum);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ReldatPacket other = (ReldatPacket) obj;
		return other.getHeader().equals(this.getHeader());
	}
}
