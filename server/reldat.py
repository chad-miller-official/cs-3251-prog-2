import socket
from server.packet import PacketIterator, Packet

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

        while True:
            data, address   = self.in_socket.recvfrom(1024)
            packet          = Packet(data)

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


