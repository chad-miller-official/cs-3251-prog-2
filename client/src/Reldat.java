import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Reldat {
    /**
     The next four functions construct and deconstruct packets.
     Packet structure is as follows:

     0000[R][A][C][O]               1 byte
     [Sequence Number]              4 bytes
     [ACK Number]                   4 bytes
     [Payload Checksum]            16 bytes
     [Header Checksum]             16 bytes
     -----------------------------
     [ P   A   Y   L   O   A   D ]

     R = Retransmission bit (1 if data in payload has already been transmitted
     before; false otherwise)
     A = ACK bit (1 if packet is an acknowledgement; false otherwise)
     C = Request for connection close bit (only 1 during connection open process)
     O = Request for connection open bit (only 1 during connection close process)
     */

    static long ULL = 0xFFFFFFFF;

    static void CONTRUCT_HEADER(String data, String seq_num, int ack_num, int [] flags)
            throws NoSuchAlgorithmException {
        
    }
}
