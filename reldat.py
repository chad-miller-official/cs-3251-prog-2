from subprocess import check_output

class Reldat( object ):
    def __init__( self, max_window_size ):
        self.src_max_window_size = max_window_size

    def get_src_max_window_size( self ):
        return self.src_max_window_size
