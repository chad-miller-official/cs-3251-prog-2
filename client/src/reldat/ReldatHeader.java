package reldat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/*
 * Packet structure is as follows:
 * 
 * 0[N][E][D][R][A][C][O]         1 byte
 * [Sequence Number]              4 bytes
 * [ACK Number]                   4 bytes
 * [Payload Size]                 4 bytes
 * [Payload Checksum]            16 bytes
 * [Header Checksum]             16 bytes
 * -----------------------------
 * [ P   A   Y   L   O   A   D ]
 * 
 * N = Packet is used to ensure the connection has not been unexpectedly terminated
 * E = Packet is EOD notification
 * D = Packet contains data
 * R = Retransmission bit (1 if data in payload has already been transmitted
 *     before; false otherwise)
 * A = ACK bit (1 if packet is an acknowledgement; false otherwise)
 * C = Request for connection close bit (only 1 during connection open process)
 * O = Request for connection open bit (only 1 during connection close process)
 */

public class ReldatHeader
{
	public static final short PACKET_HEADER_SIZE  = 1 + 4 + 4 + 4 + 16 + 16;
	
	public static final byte OPEN_FLAG  	 = 0b00000001;
	public static final byte CLOSE_FLAG 	 = 0b00000010;
	public static final byte ACK_FLAG   	 = 0b00000100;
	public static final byte RETRANSMIT_FLAG = 0b00001000;
	public static final byte DATA_FLAG       = 0b00010000;
	public static final byte EOD_FLAG        = 0b00100000;
	public static final byte NUDGE_FLAG      = 0b01000000;
	public static final byte RESERVE_FLAG_4  = (byte) 0b10000000;
	
	private byte flags;
	private int seqNum;
	private int ackNum;
	private int payloadSize;
	private byte[] payloadChecksum;

	public ReldatHeader( byte flags, int seqNum, int ackNum, byte[] data )
	{
		this.flags       = flags;
		this.seqNum      = seqNum;
		this.ackNum      = ackNum;
		this.payloadSize = data.length;
		
		try
		{
			MessageDigest checksumGenerator = MessageDigest.getInstance( "MD5" );
			payloadChecksum = checksumGenerator.digest( data );
		}
		catch( NoSuchAlgorithmException e )
		{
			e.printStackTrace();
		}
	}
	
	// Private constructor for bytesToHeader()
	private ReldatHeader( byte[] payloadChecksum, byte flags, int seqNum, int ackNum, int payloadSize )
	{
		this.flags           = flags;
		this.seqNum          = seqNum;
		this.ackNum          = ackNum;
		this.payloadSize     = payloadSize;
		this.payloadChecksum = payloadChecksum;
	}
	
	public byte getFlags()
	{
		return flags;
	}
	
	public int getSequenceNumber()
	{
		return seqNum;
	}
	
	public int getAcknowledgementNumber()
	{
		return ackNum;
	}
	
	public byte[] getPayloadChecksum()
	{
		return payloadChecksum;
	}
	
	public int getPayloadSize()
	{
		return payloadSize;
	}
	
	public byte[] toBytes()
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream output     = new DataOutputStream( bytes );      
		
		try
		{
			output.writeByte( flags );
			output.writeInt( seqNum );
			output.writeInt( ackNum );
			output.writeInt( payloadSize );
			output.write( payloadChecksum );
			output.close();
		}
		catch( IOException e )
		{
			e.printStackTrace();
		}

		return bytes.toByteArray();
	}
	
	public void addFlag(byte flag)
	{
		this.flags |= flag;
	}
	
	public static ReldatHeader bytesToHeader( byte[] header )
	{
		byte flags      = header[0];
		int seqNum      = ((0xFF & header[1]) << 24) | ((0xFF & header[2])  << 16) | ((0xFF & header[3])  << 8) | (0xFF & header[4]);
		int ackNum      = ((0xFF & header[5]) << 24) | ((0xFF & header[6])  << 16) | ((0xFF & header[7])  << 8) | (0xFF & header[8]);
		int payloadSize = ((0xFF & header[9]) << 24) | ((0xFF & header[10]) << 16) | ((0xFF & header[11]) << 8) | (0xFF & header[12]);
				
		byte[] payloadChecksum = new byte[16];
		
		System.arraycopy( header, 13, payloadChecksum, 0, 16 );

		return new ReldatHeader( payloadChecksum, flags, seqNum, ackNum, payloadSize );
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ackNum;
		result = prime * result + flags;
		result = prime * result + Arrays.hashCode(payloadChecksum);
		result = prime * result + payloadSize;
		result = prime * result + seqNum;
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
		ReldatHeader other = (ReldatHeader) obj;
		if(this.seqNum == 0)
			return this.ackNum == other.seqNum;
		else
			return this.seqNum == other.ackNum;
	}
}
