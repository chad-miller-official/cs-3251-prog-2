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
	public static final short PACKET_HEADER_SIZE  = 1 + 1 + 4 + 4 + 16 + 16;
	public static final short PACKET_PAYLOAD_SIZE = MAX_PACKET_SIZE - PACKET_HEADER_SIZE;
	
	public static final byte OPEN_FLAG  	 = 0b00000001;
	public static final byte CLOSE_FLAG 	 = 0b00000010;
	public static final byte ACK_FLAG   	 = 0b00000100;
	public static final byte RETRANSMIT_FLAG = 0b00001000;
	public static final byte RESERVE_FLAG_1  = 0b00010000;
	public static final byte RESERVE_FLAG_2  = 0b00100000;
	public static final byte RESERVE_FLAG_3  = 0b01000000;
	public static final byte RESERVE_FLAG_4  = (byte) 0b10000000;

	private ReldatUtil() throws IllegalStateException
	{
		throw new IllegalStateException();
	}

	private static void constructHeader( String data, int seqNum, int ackNum, byte... flags )
	{
		// TODO
	}
	
	private static Header deconstructHeader()
	{
		// TODO
		return null;
	}
	
	public static String constructPacket( String data, int seqNum, int ackNum, byte... flags )
	{
		// TODO
		return null;
	}
	
	public static void deconstructPacket( String packetData )
	{
		// TODO
	}
	
	private class Header
	{
		public byte flags;
		public int seqNum;
		public int ackNum;
		public String payloadChecksum;
		public String headerChecksum;

		public Header( byte flags, int seqNum, int ackNum, String payloadChecksum, String headerChecksum )
		{
			this.flags           = flags;
			this.seqNum          = seqNum;
			this.ackNum          = ackNum;
			this.payloadChecksum = payloadChecksum;
			this.headerChecksum  = headerChecksum;
		}
	}
}