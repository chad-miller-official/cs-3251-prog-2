package reldat;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import reldat.exception.HeaderCorruptedException;
import reldat.exception.PayloadCorruptedException;

public class ReldatPacket
{
	public static final short MAX_PACKET_SIZE     = 1000;
	public static final short PACKET_PAYLOAD_SIZE = MAX_PACKET_SIZE - ReldatHeader.PACKET_HEADER_SIZE;

	private ReldatHeader header;
	private byte[] headerChecksum;
	private byte[] data;

	public ReldatPacket( int data, byte flags, int seqNum, int ackNum )
	{
		this( Integer.toString( data ), flags, seqNum, ackNum );
	}

	public ReldatPacket( String data, byte flags, int seqNum, int ackNum )
	{
		this( data.getBytes(), flags, seqNum, ackNum );
	}

	public ReldatPacket( byte[] data, byte flags, int seqNum, int ackNum )
	{
		this.data = data;
		header    = new ReldatHeader( flags, seqNum, ackNum, data );

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
		return data;
	}

	public byte[] toBytes()
	{
		byte[] headerBytes = header.toBytes();
		byte[] packetBytes = new byte[ headerBytes.length + headerChecksum.length + data.length ];

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
			data,
			0,
			packetBytes,
			headerBytes.length + headerChecksum.length,
			data.length
		);

		return packetBytes;
	}

	public DatagramPacket toDatagramPacket( InetAddress dstIPAddress, int port )
	{
		byte[] thisBytes = toBytes();
		return new DatagramPacket( thisBytes, thisBytes.length, dstIPAddress, port);
	}

	public static ReldatPacket bytesToPacket( byte[] packetData ) throws HeaderCorruptedException, PayloadCorruptedException
	{
		byte[] headerBytes    = new byte[ReldatHeader.PACKET_HEADER_SIZE - 16];
		byte[] headerChecksum = new byte[16];
		byte[] payloadBytes   = new byte[packetData.length - ReldatHeader.PACKET_HEADER_SIZE];

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

		System.arraycopy(
			packetData,
			ReldatHeader.PACKET_HEADER_SIZE,
			payloadBytes,
			0,
			packetData.length - ReldatHeader.PACKET_HEADER_SIZE
		);

		try
		{
			MessageDigest checksumGenerator = MessageDigest.getInstance( "MD5" );

			byte[] expectedHeaderChecksum = checksumGenerator.digest( headerBytes );
			checksumGenerator.reset();

			if( !Arrays.equals( headerChecksum, expectedHeaderChecksum ) )
				throw new HeaderCorruptedException();

			ReldatHeader header = ReldatHeader.bytesToHeader( headerBytes );

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
}
