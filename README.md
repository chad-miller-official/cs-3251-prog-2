# CS 3251 - Reliable Data Transfer 04/03/2017
## Project Group
*Chad Miller -cmiller86@gatech.edu
*Noam Lerner - nlerner3@gatech.edu
*Robert Bradshaw

## Compiling and Running

### Server
from root directory run `$ python ./server/reldat-server.py `

## Files submitted

### Server Files
##### reldat.py
This file contains the reldat server which implements the protocol. 3 of the functions are meant to be called in
an external loop which are
 *listen - listens for incoming packets
 *resend_packets() - resent unacked packets that have timed out
 *check_connection() - ensures that the client is still on the other end of the connection

##### packet.py
Contains helper function for dealing with packets as well as a packet class for handling packets on the server.

##### reldat-server.py
The entry point for the server. Boots the server up and starts a loop which will continually call the 3 required functions

## Design Documentation

All methods are documented in the files.

### Packet Design
    0[N][E][D][R][A][C][O]           1 byte
    [Sequence Number]              4 bytes
    [ACK Number]                   4 bytes
    [Payload Size]                 4 bytes
    [Payload Checksum]            16 bytes
    [Header Checksum]             16 bytes
    -----------------------------
    [ P   A   Y   L   O   A   D ]

    N = Nudge - meant to check the connection
    E = Packet is end-of-data
    D = Packet contains data
    R = Retransmission bit (1 if data in payload has already been transmitted
        before; false otherwise)
    A = ACK bit (1 if packet is an acknowledgement; false otherwise)
    C = Request for connection close bit (only 1 during connection open process)
    O = Request for connection open bit (only 1 during connection close process)

### Basic Protocol
The client initiations a connection by sending a packet with the O flag set. A three way handshake is then done
A four way handshake is done for disconnecting when the server recieved a packet with the C flag set..
Every packet is separately ackd. If any packet has been retransmitted 3 times and has gone unacked, the connection
is closed. This is the only way the server will close a connection without receiving a packet with the C flag set.
While a connection is open, if no packets are being sent, the server will send packets with the N flag known as a
Nudge packet. Once a Nudge packet is sent, it is treated as a normal packet and an ack is epected for it.

