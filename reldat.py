from subprocess import check_output

class Reldat( object ):
    # if dest_ip is none, then we are the server
    def __init__( self, port, src_max_window_size, dest_ip=None ):
        self.dst_ip              = dest_ip
        self.dst_max_window_size = None

        self.src_ip              = check_output( [ 'hostname', '-I' ] ).split()[0]
        self.src_max_window_size = src_max_window_size

        self.port = port

    def get_dst_ip_address( self ):
        return self.dst_ip

    def get_src_ip_address( self ):
        return self.src_ip

    def get_port( self ):
        return self.port

    def get_dst_max_window_size( self ):
        return self.dst_max_window_size

    def get_src_max_window_size( self ):
        return self.src_max_window_size
