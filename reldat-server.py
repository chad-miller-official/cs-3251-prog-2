#!/usr/bin/python

import socket
import sys

import reldat

def listen_loop( reldat_conn ):
    disconnect = False

    while not disconnect:
        # TODO
        pass

def usage():
    print 'Usage: ./reldat-server.py <port> <max receive window size in packets>'
    sys.exit( 0 )

def main( argv ):
    if len( argv ) != 2:
        usage()

    port                    = int( argv[0] )
    max_receive_window_size = int( argv[1] )

    if port > 65535:
        usage()

    reldat_conn = reldat.Reldat( max_receive_window_size )
    reldat_conn.listen( port )
    listen_loop( reldat_conn )

if __name__ == "__main__":
    main( sys.argv[1:] )

sys.exit( 0 )
