import hashlib
import math
import pickle
import socket
import struct

'''
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
'''

_ULL = 0xFFFFFFFFFFFFFFFF

def _construct_header( data, seq_num, ack_num, flags=[] ):
    header_flags = 0

    for flag in flags:
        header_flags |= flag

    hash_calc = hashlib.md5()
    hash_calc.update( data )
    checksum = int( hash_calc.hexdigest(), 16 )

    checksum_parts = (
        ( checksum >> 64 ) & _ULL,
        checksum & _ULL
    )

    return struct.pack(
        '!'   # use network order
        'B'   # flags
        'I'   # seq num
        'I'   # ack num
        '2Q', # checksum
        header_flags, seq_num, ack_num, checksum_parts[0], checksum_parts[1]
    )

def _construct_packet( data, seq_num, ack_num, flags=[] ):
    header = _construct_header( data, seq_num, ack_num, flags )

    hash_calc = hashlib.md5()
    hash_calc.update( header )

    header_checksum       = int( hash_calc.hexdigest(), 16 )
    header_checksum_parts = (
        ( header_checksum >> 64 ) & _ULL,
        header_checksum & _ULL
    )

    header_checksum_struct = struct.pack(
        '!'   # use network order
        '2Q', # checksum
        header_checksum_parts[0], header_checksum_parts[1]
    )

    return header + header_checksum_struct + data

def _deconstruct_header( packet_header ):
    ( flags, seq_num, ack_num, checksum_1, checksum_2 ) = struct.unpack(
        '!BII2Q',
        packet_header
    )

    checksum = ( ( checksum_1 & _ULL ) << 64 ) | ( checksum_2 & _ULL )
    return ( flags, seq_num, ack_num, checksum )

def _deconstruct_packet( packet_data ):
    packet_header   = packet_data[:PACKET_HEADER_SIZE - 16]
    header_checksum = packet_data[PACKET_HEADER_SIZE - 16 : PACKET_HEADER_SIZE]
    packet_payload  = packet_data[PACKET_HEADER_SIZE:]

    ( header_checksum_1, header_checksum_2 ) = struct.unpack(
        '!2Q',
        header_checksum
    )

    header_checksum = ( ( header_checksum_1 & _ULL ) << 64 ) | ( header_checksum_2 & _ULL )

    hash_calc = hashlib.md5()
    hash_calc.update( packet_header )
    expected_header_checksum = hash_calc.hexdigest()

    if header_checksum != int( expected_header_checksum, 16 ):
        raise HeaderCorruptedError()

    ( flags, seq_num, ack_num, checksum ) = _deconstruct_header( packet_header )

    hash_calc = hashlib.md5()
    hash_calc.update( packet_payload )
    expected_checksum = hash_calc.hexdigest()

    if checksum != int( expected_checksum, 16 ):
        raise PayloadCorruptedError()

    return ( flags, seq_num, ack_num, packet_payload )

'''
The following class defines a RELDAT communication channel object.
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
    def __init__( self, max_window_size ):
        self.src_ip_address      = socket.gethostbyname( socket.gethostname() )
        self.src_max_window_size = max_window_size

        self.dst_ip_address      = None
        self.dst_max_window_size = None

        self.port       = None
        self.in_socket  = None
        self.out_socket = None

    def connect( self, dst_ip_address, port ):
        self.dst_ip_address = dst_ip_address
        self.port           = port
        self.in_socket      = socket.socket( socket.AF_INET, socket.SOCK_DGRAM )
        self.out_socket     = socket.socket( socket.AF_INET, socket.SOCK_DGRAM )

        self.in_socket.bind( ( self.src_ip_address, self.port ) )

        # TODO

    def listen( self, port ):
        self.port       = port
        self.in_socket  = socket.socket( socket.AF_INET, socket.SOCK_DGRAM )
        self.out_socket = socket.socket( socket.AF_INET, socket.SOCK_DGRAM )

        self.in_socket.bind( ( self.src_ip_address, self.port ) )

        # TODO

    def send( self, data ):
        packetizer = PacketIterator( data, self.dst_max_window_size )

        for packet in packetizer:
            # TODO
            pass

    def recv( self ):
        # TODO
        pass

    def disconnect( self ):
        # TODO
        pass

'''
The following class defines a black box that accepts data and packetizes
it. Every iteration returns the next packet that composes the data.
'''

class PacketIterator:
    def __init__( self, data, window_size ):
        self.data        = data
        self.window_size = window_size

        self.last_packet_num = int( math.ceil( len( data ) / float( PACKET_PAYLOAD_SIZE ) ) )
        self.curr_packet_num = 0

    def __iter__( self ):
        return self

    def next( self ):
        if self.curr_packet_num >= self.last_packet_num:
            raise StopIteration
        else:
            send_data_start = self.curr_packet_num * PACKET_PAYLOAD_SIZE
            send_data_end   = ( self.curr_packet_num + 1 ) * PACKET_PAYLOAD_SIZE

            if send_data_end > len( self.data ):
                send_data = self.data[send_data_start:]
            else:
                send_data = self.data[send_data_start : send_data_end]

            self.curr_packet_num += 1
            packet                = _construct_packet( send_data, self.curr_packet_num, 0 )

            return packet

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
