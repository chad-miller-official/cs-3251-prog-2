import socket
from server.packet import PacketIterator, Packet, SYN_ACK

class Reldat( object ):
    def __init__( self, max_window_size ):
        self.src_ip_address      = socket.gethostbyname( socket.gethostname() )
        self.src_max_window_size = max_window_size

        self.dst_ip_address      = None
        self.dst_max_window_size = None

        self.port       = None
        self.socket = None

        # Need to ACk
        self.seqs_recd  = []
        # Waiting for ack
        self.seqs_sent = []

    def establish_connection( self, dst_ip_address, packet):
        self.dst_ip_address = dst_ip_address
        self.dst_max_window_size = int(packet.payload)

        self.on_seq = 0
        synack      = SYN_ACK(self.get_seq_num(), packet.seq_num, self.src_max_window_size)

        self.socket.sendto(synack, dst_ip_address)

        while True:
            data, address   = self.socket.recvfrom(1024)
            packet          = Packet(data)
            if packet.is_ack() and self.ack_recd(packet):
                self.conversate()
            # TODO add timer with break

    def ack_recd(self, packet):
        if packet.is_ack() and packet.ack_num in self.seqs_sent:
            self.seqs_sent.remove(packet.ack_num)
            return True
        return False

    def send_ack(self, packet):
        #     TODO
        pass

    def get_seq_num(self):
        self.seqs_sent.append(self.on_seq)
        self.on_seq += 1
        # TODO add timer and events
        return self.seqs_sent[-1]

    def listen( self, port ):
        self.port       = port
        self.socket  = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

        self.socket.bind((self.src_ip_address, self.port))

        while True:
            data, address   = self.socket.recvfrom(1024)
            packet          = Packet(data)
            if packet.is_open():
                self.establish_connection(address, packet)

    def conversate(self):
        while True:
            packet = self.recv()
            self.send(packet.payload.upper())

    def send( self, data ):
        packetizer = PacketIterator( data, self.dst_max_window_size, self.get_seq_num )

        for packet in packetizer:
            self.socket.sendto(packet, self.dst_ip_address)

    def recv( self ):
        while True:
            data, address = self.socket.recvfrom(1024)
            if address == self.dst_ip_address:
                packet = Packet(data)
                if packet.is_ack():
                    self.ack_recd(packet)
                else:
                    self.send_ack(packet)
                    return packet

    def disconnect( self ):
        # TODO
        pass


