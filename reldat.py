import hashlib
import pickle
import socket
import struct

from subprocess import check_output

'''
Packet structure:
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
'''

# All sizes are in bytes
MAX_PACKET_SIZE     = 1000
PACKET_HEADER_SIZE  = 1 + 4 + 4 + 16 + 16
PACKET_PAYLOAD_SIZE = MAX_PACKET_SIZE - PACKET_HEADER_SIZE

# Header flags
OPEN_FLAG       = 0b00000001
CLOSE_FLAG      = 0b00000010
ACK_FLAG        = 0b00000010
RETRANSMIT_FLAG = 0b00001000
RESERVE_FLAG_1  = 0b00010000
RESERVE_FLAG_2  = 0b00100000
RESERVE_FLAG_3  = 0b01000000
RESERVE_FLAG_4  = 0b10000000

class Reldat( object ):
    # Constants
    __ULL = 0xFFFFFFFFFFFFFFFF

    def __init__( self, max_window_size ):
        self.src_ip_address      = socket.gethostbyname( socket.gethostname() )
        self.src_max_window_size = max_window_size

        self.dst_ip_address      = None
        self.dst_max_window_size = None

        self.port       = None
        self.in_socket  = None
        self.out_socket = None

    def __construct_header( self, data, seq_num, ack_num, flags=[] ):
        header_flags = 0

        for flag in flags:
            header_flags |= flag

        hash_calc = hashlib.md5()
        hash_calc.update( data )
        checksum = int( hash_calc.hexdigest(), 16 )

        checksum_parts = (
            ( checksum >> 64 ) & self.__ULL,
            checksum & self.__ULL
        )

        return struct.pack(
            '!'   # use network order
            'B'   # flags
            'I'   # seq num
            'I'   # ack num
            '2Q', # checksum
            header_flags, seq_num, ack_num, checksum_parts[0], checksum_parts[1]
        )

    def __construct_packet( self, data, seq_num, ack_num, flags=[] ):
        header = self.__construct_header( data, seq_num, ack_num, flags )

        hash_calc = hashlib.md5()
        hash_calc.update( header )

        header_checksum       = int( hash_calc.hexdigest(), 16 )
        header_checksum_parts = (
            ( header_checksum >> 64 ) & self.__ULL,
            header_checksum & self.__ULL
        )

        header_checksum_struct = struct.pack(
            '!2Q',
            header_checksum_parts[0], header_checksum_parts[1]
        )

        return header + header_checksum_struct + data

    def __deconstruct_header( self, packet_header ):
        ( flags, seq_num, ack_num, checksum_1, checksum_2 ) = struct.unpack(
            '!B2I2Q',
            packet_header
        )

        checksum = ( ( checksum_1 & self.__ULL ) << 64 ) | ( checksum_2 & self.__ULL )
        return ( flags, seq_num, ack_num, checksum )

    def __deconstruct_packet( self, packet_data ):
        packet_header   = packet_data[:PACKET_HEADER_SIZE - 16]
        header_checksum = packet_data[PACKET_HEADER_SIZE - 16 : PACKET_HEADER_SIZE]
        packet_payload  = packet_data[PACKET_HEADER_SIZE:]

        ( header_checksum_1, header_checksum_2 ) = struct.unpack(
            '!2Q',
            header_checksum
        )

        header_checksum = ( ( header_checksum_1 & self.__ULL ) << 64 ) | ( header_checksum_2 & self.__ULL )

        hash_calc = hashlib.md5()
        hash_calc.update( packet_header )
        expected_header_checksum = hash_calc.hexdigest()

        if header_checksum != int( expected_header_checksum, 16 ):
            raise HeaderCorruptedError()

        ( flags, seq_num, ack_num, checksum ) = self.__deconstruct_header( packet_header )

        hash_calc = hashlib.md5()
        hash_calc.update( packet_payload )
        expected_checksum = hash_calc.hexdigest()

        if checksum != int( expected_checksum, 16 ):
            raise PayloadCorruptedError()

        return ( flags, seq_num, ack_num, packet_payload )

    def connect( self, dst_ip_address, port ):
        self.dst_ip_address = dst_ip_address
        self.port           = port
        self.in_socket      = socket.socket( socket.AF_INET, socket.SOCK_DGRAM )
        self.out_socket     = socket.socket( socket.AF_INET, socket.SOCK_DGRAM )

        self.in_socket.bind( ( self.src_ip_address, self.port ) )

        payload         = [ self.src_ip_address, self.src_max_window_size ]
        payload_encoded = pickle.dumps( payload )

        # Construct the initial request for open packet
        packet = self.__construct_packet( payload_encoded, 0, 0, [ OPEN_FLAG ] )

        # TODO

    def listen( self, port ):
        self.port       = port
        self.in_socket  = socket.socket( socket.AF_INET, socket.SOCK_DGRAM )
        self.out_socket = socket.socket( socket.AF_INET, socket.SOCK_DGRAM )

        self.in_socket.bind( ( self.src_ip_address, self.port ) )

        # TODO

    def disconnect( self ):
        # TODO
        pass

'''
The following classes define exceptions that may be raised during transmission.
These should be handled appropriately so as not to prematurely kill the client
or the server.
'''

class HeaderCorruptedError( Exception ):
    def __init__( self ):
        return

class PayloadCorruptedError( Exception ):
    def __init__( self ):
        return
