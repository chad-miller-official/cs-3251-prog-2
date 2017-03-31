import socket
from packet import PacketIterator, Packet, ACK, SYNACK, CLOSEACK, CLOSE, EODACK, DATA_FLAG, EOD_FLAG, RETRANSMIT_FLAG, _deconstruct_packet, _construct_packet
import datetime
from time import sleep

class Reldat( object ):
    def __init__( self, max_window_size ):
        self.src_ip_address      = socket.gethostbyname( socket.gethostname() )
        self.src_max_window_size = max_window_size

        self.dst_ip_address      = None
        self.dst_max_window_size = None
        self.on_handshake        = 0

        self.port       = None
        self.in_socket  = None
        self.out_socket = None
        self.timeout = 3 #seconds

        # Need to ACK
        self.seqs_recd = []

        # Waiting for ACK
        self.seqs_sent = []
        self.timers    = {}

        self.pkt_buffer = [None for _ in range(self.src_max_window_size)]

        self.on_seq = 0;
        self.eod_recd = False;

    def ack_recd( self, packet ):
        if packet.is_ack() and packet.ack_num in self.seqs_sent:
            self.seqs_sent.remove( packet.ack_num )
            self.timers[packet.seq_num] = None
            return True

        return False

    def send_ack(self, packet, eod=False):
        if eod:
            ack_pkt = EODACK(packet.seq_num)
        else:
            ack_pkt = ACK(packet.seq_num)

        self.out_socket.sendto(ack_pkt, self.dst_ip_address)

    def get_seq_num(self):
        self.seqs_sent.append(self.on_seq)
        self.on_seq += 1
        # TODO add timer and events
        return self.seqs_sent[-1]

    def open_socket(self, port):
        self.port       = port
        self.in_socket  = socket.socket( socket.AF_INET, socket.SOCK_DGRAM )
        self.out_socket = socket.socket( socket.AF_INET, socket.SOCK_DGRAM )
        
        self.in_socket.settimeout(1)

        self.in_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.in_socket.bind( ( self.src_ip_address, self.port ) )

        print "Listening on port " + str( self.port ) + "."

    ind_start = -1
    all_data  = ""

    def listen( self ):
        try:
            data, address = self.in_socket.recvfrom( 1024 )
            packet        = Packet( data )
    
            if not self.has_connection():
                self.establish_connection( address, packet )
            elif packet.is_close() and self.has_connection():
                self.disconnect( packet )
                return
            elif packet.is_data():
                self.seqs_recd.append(packet.seq_num)
    
                if (self.buffer_full() and not packet.is_retransmit()):
                    data             = self.flush_buffer()
                    Reldat.all_data += data
                    self.send(data)
                    Reldat.ind_start = -1
    
                if Reldat.ind_start < 0:
                    Reldat.ind_start = packet.seq_num
    
                print packet.payload
                print "seq: " + str(packet.seq_num)
                print "ack: " + str(packet.ack_num)
                print "flag: " + str(packet.flag)
                print str(packet.seq_num) + "/" + str(Reldat.ind_start)
    
                index = packet.seq_num - Reldat.ind_start
    
                if (not packet.is_retransmit() or packet.seq_num not in self.seqs_recd):
                    self.print_buffer()
                    self.pkt_buffer[index] = packet
                    print self.pkt_buffer
    
                #sleep(1.4)
                self.send_ack(packet)
            elif packet.is_ack():
                print "Received ACK: " + str(packet.ack_num)
                try:
                    del self.timers[str(packet.ack_num)]
                except KeyError:
                    # If we get here, it means the server sent us an ACK
                    # for the same packet twice, possibly due to network
                    # delays. These can be ignored.
                    pass
            
                print self.timers
                self.print_buffer()

                if not self.timers and self.buffer_empty() and self.eod_recd:
                    print 'Sending EOD'
                    eod = _construct_packet( '', self.get_seq_num(), 0, [ EOD_FLAG ])
                    self._send_raw_packet(eod)
                    self.eod_recd = False
                    print self.timers
            elif packet.is_eod():
                print "Received EOD"
                self.eod_recd = True
                self.send_ack(packet, True)
    
                data = self.flush_buffer()
                Reldat.all_data += data
                self.send(data)
    
                print "Total data: " + Reldat.all_data
        except socket.timeout:
            print "TIMEOUT lol"
            pass


    
    def print_buffer(self):
        print '[',

        for packet in self.pkt_buffer:
            if packet is not None:
                print '"' + packet.payload + '", ',
            else:
                print 'None, ',
        
        print ']'

    def establish_connection( self, dst_ip_address, packet ):
        print "Attempting to establish connection with " + str( dst_ip_address[0] ) + ":" + str( self.port ) + "."
        if self.on_handshake is 0:
            if not packet.is_open():
                if packet.is_ack():
                    print "Recieved SynAck"
                    if self.timers.get(str(packet.ack_num)) is not None:
                        self.on_handshake += 1
                        print "Received ACK: " + str(packet.ack_num)
                        try:
                            del self.timers[str(packet.ack_num)]
                        except KeyError:
                            # If we get here, it means the server sent us an ACK
                            # for the same packet twice, possibly due to network
                            # delays. These can be ignored.
                            pass
            else:
                print "Received SYN (packet 1/3)."
                self.dst_ip_address      = ( dst_ip_address[0], self.port + 1 ) # XXX DEBUG TODO REMOVE
                self.dst_max_window_size = int( packet.payload )

                synack = SYNACK(str(self.src_max_window_size))
                self._send_raw_packet(synack)
                print "Sent SYNACK (packet 2/3)."
        if self.on_handshake is 1:
                print "Connection Established"


        data, address = self.in_socket.recvfrom( 1024 )
        packet        = Packet( data )

        if packet.is_ack():
            print "Received ACK (packet 3/3)."
            print packet.payload

        print "Connection established."

    def buffer_empty(self):
        for data in self.pkt_buffer:
            if (data is not None):
                return False
        
        return True

    def buffer_full(self):
        for data in self.pkt_buffer:
            if (data is None):
                return False
        return True

    def flush_buffer(self):
        buffered_data = ""

        for pkt in self.pkt_buffer:
            if (pkt is not None):
                buffered_data += pkt.payload

        self.pkt_buffer = [None for _ in range(self.src_max_window_size)]
        return buffered_data

    def resend_packets(self):
        for index in self.timers:
            if datetime.datetime.now() - self.timers[index]['time'] > datetime.timedelta(seconds=self.timeout):
                print "Resending seq " + index
                self._send_raw_packet(self.timers[index]['packet'], True)

    def send( self, data ):
        packetizer = PacketIterator( data.upper(), self.dst_max_window_size, self.get_seq_num )

        for packet in packetizer:
            self._send_raw_packet(packet)

    def _send_raw_packet(self, packet, retransmit=False):
        self.out_socket.sendto(packet, self.dst_ip_address)
        sent = Packet(packet)
        
        if retransmit:
            sent.add_flag(RETRANSMIT_FLAG)
        
        print "EMPLACING INTO self.timers: " + str(sent.seq_num)
        self.timers[str(sent.seq_num)] = {
            'time':datetime.datetime.now(),
            'packet':packet
        }

    def disconnect( self, close ):
        print "Attempting to disconnect from " + str( self.dst_ip_address ) + ":" + str( self.port ) + "."
        print "Received CLOSE (packet 1/4)."

        closeack = CLOSEACK()
        self.out_socket.sendto( closeack, self.dst_ip_address )

        print "Sent CLOSEACK (packet 2/4)."

        close = CLOSE()
        self.out_socket.sendto( close, self.dst_ip_address )

        print "Sent server-side CLOSE (packet 3/4)."

        data, address = self.in_socket.recvfrom( 1024 )
        packet        = Packet( data )

        if packet.is_ack():
            print "Received CLOSEACK (packet 4/4)."

        print "Connection terminated."

        self.dst_ip_address      = None
        self.dst_max_window_size = None
        
        self.seqs_recd = []

        self.seqs_sent = []
        self.timers    = {}

        self.pkt_buffer = [None for _ in range(self.src_max_window_size)]

        self.on_seq = 0;
        
        ind_start = -1
        all_data  = ""

    def has_connection(self):
        return self.dst_ip_address is not None and self.on_handshake == 1
