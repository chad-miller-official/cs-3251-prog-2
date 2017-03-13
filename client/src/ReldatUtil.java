import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/*
 * The next four functions construct and deconstruct packets.
 * Packet structure is as follows:
 * 
 * 0000[R][A][C][O]               1 byte
 * [Sequence Number]              4 bytes
 * [ACK Number]                   4 bytes
 * [Payload Checksum]            16 bytes
 * [Header Checksum]             16 bytes
 * -----------------------------
 * [ P   A   Y   L   O   A   D ]
 * 
 * R = Retransmission bit (1 if data in payload has already been transmitted
 *     before; false otherwise)
 * A = ACK bit (1 if packet is an acknowledgement; false otherwise)
 * C = Request for connection close bit (only 1 during connection open process)
 * O = Request for connection open bit (only 1 during connection close process)
 */

public final class ReldatUtil
{
	public static final short MAX_PACKET_SIZE     = 1000;
	public static final short PACKET_HEADER_SIZE  = 1 + 4 + 4 + 16 + 16;
	public static final short PACKET_PAYLOAD_SIZE = MAX_PACKET_SIZE - PACKET_HEADER_SIZE;
	
	public static final byte OPEN_FLAG  	 = 0b00000001;
	public static final byte CLOSE_FLAG 	 = 0b00000010;
	public static final byte ACK_FLAG   	 = 0b00000100;
	public static final byte RETRANSMIT_FLAG = 0b00001000;
	public static final byte RESERVE_FLAG_1  = 0b00010000;
	public static final byte RESERVE_FLAG_2  = 0b00100000;
	public static final byte RESERVE_FLAG_3  = 0b01000000;
	public static final byte RESERVE_FLAG_4  = (byte) 0b10000000;
	
	private static MessageDigest checksumGenerator;

	static
	{
		try
		{
			checksumGenerator = MessageDigest.getInstance( "MD5" );
		}
		catch( NoSuchAlgorithmException e )
		{
			e.printStackTrace();
		}
	}

	private ReldatUtil() throws IllegalStateException
	{
		throw new IllegalStateException();
	}

	private static byte[] constructHeader( String data, int seqNum, int ackNum, byte... flags )
	{
		byte headerFlags = 0;
		
		for( byte flag : flags )
			headerFlags |= flag;
		
		byte[] payloadChecksum = checksumGenerator.digest( data.getBytes() );
		checksumGenerator.reset();
		
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream output     = new DataOutputStream( bytes );      
		
		try
		{
			output.writeByte( headerFlags );
			output.writeInt( seqNum );
			output.writeInt( ackNum );
			output.write( payloadChecksum );
			output.close();
		}
		catch( IOException e )
		{
			e.printStackTrace();
		}

		return bytes.toByteArray();
	}
	
	private static Header deconstructHeader( byte[] header )
	{
		byte flags = header[0];
		int seqNum = ( header[1] << 24 ) | ( header[2] << 16 ) | ( header[3] << 8 ) | header[4];
		int ackNum = ( header[5] << 24 ) | ( header[6] << 16 ) | ( header[7] << 8 ) | header[8];
		
		byte[] payloadChecksum = new byte[16];
		
		System.arraycopy( header, 9,      payloadChecksum, 0, 16 );

		return new Header( flags, seqNum, ackNum, payloadChecksum );
	}
	
	public static byte[] constructPacket( String data, int seqNum, int ackNum, byte... flags )
	{
		byte[] header         = constructHeader( data, seqNum, ackNum, flags );
		byte[] dataBytes      = data.getBytes();
		byte[] headerChecksum = checksumGenerator.digest( header );
		
		checksumGenerator.reset();

		byte[] fullPacket = new byte[ header.length + headerChecksum.length + dataBytes.length ];
		
		System.arraycopy( header,         0, fullPacket, 0,                                     header.length         );
		System.arraycopy( headerChecksum, 0, fullPacket, header.length,                         headerChecksum.length );
		System.arraycopy( dataBytes,      0, fullPacket, header.length + headerChecksum.length, dataBytes.length      );
		
		return fullPacket;
	}
	
	public static Packet deconstructPacket( byte[] packetData ) throws HeaderCorruptedException, PayloadCorruptedException
	{
		byte[] headerBytes    = new byte[PACKET_HEADER_SIZE - 16];
		byte[] headerChecksum = new byte[16];
		byte[] payloadBytes   = new byte[packetData.length - PACKET_HEADER_SIZE];
		
		System.arraycopy( packetData, 0,                       headerBytes,    0, PACKET_HEADER_SIZE - 16                );
		System.arraycopy( packetData, PACKET_HEADER_SIZE - 16, headerChecksum, 0, 16                                     );
		System.arraycopy( packetData, PACKET_HEADER_SIZE,      payloadBytes,   0, packetData.length - PACKET_HEADER_SIZE );
	
		Header header = deconstructHeader( headerBytes );
		
		byte[] expectedHeaderChecksum = checksumGenerator.digest( headerBytes );
		checksumGenerator.reset();
		
		if( !Arrays.equals( headerChecksum, expectedHeaderChecksum ) )
			throw new HeaderCorruptedException();
		
		byte[] expectedPayloadChecksum = checksumGenerator.digest( payloadBytes );
		checksumGenerator.reset();
		
		if( !Arrays.equals( header.payloadChecksum, expectedPayloadChecksum ) )
			throw new PayloadCorruptedException();
		
		return new Packet( header, new String( payloadBytes ) );
	}
	
	private static class Header
	{
		public byte flags;
		public int seqNum;
		public int ackNum;
		public byte[] payloadChecksum;

		public Header( byte flags, int seqNum, int ackNum, byte[] payloadChecksum )
		{
			this.flags           = flags;
			this.seqNum          = seqNum;
			this.ackNum          = ackNum;
			this.payloadChecksum = payloadChecksum;
		}
	}
	
	private static class Packet
	{
		public Header header;
		public String data;
		
		public Packet( Header header, String data )
		{
			this.header = header;
			this.data   = data;
		}
	}
	
	private static class HeaderCorruptedException extends Exception
	{
		private static final long serialVersionUID = -142840450298573639L;

		public HeaderCorruptedException()
		{
			return;
		}
	}
	
	private static class PayloadCorruptedException extends Exception
	{
		private static final long serialVersionUID = -8999036302658468193L;

		public PayloadCorruptedException()
		{
			return;
		}
	}
}