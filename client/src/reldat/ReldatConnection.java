package reldat;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.*;
import java.net.*;

public class ReldatConnection
{
	private int maxWindowSize;
	private String dstIPAddress;
	private int port;
	private DatagramSocket inSocket;
	private DatagramSocket outSocket;

	public ReldatConnection( int maxWindowSize ) {
		this.maxWindowSize = maxWindowSize;
        try {
            this.outSocket = new DatagramSocket();
            this.inSocket = new DatagramSocket();
        } catch (SocketException e) {
            System.out.println("Establishing connection failed.");
        }
	}

	public void connect( String dstIPAddress, int port ){
        if (dstIPAddress == null) {
            throw new IllegalArgumentException("Invalid destination IP address.");
        }
        if (port < 0) {
            throw new IllegalArgumentException("Port must not be negative or reserved.");
        }

        if (this.outSocket != null) {
            //byte[] buffer = new byte[1000];
            try {
                InetAddress address = InetAddress.getByName(dstIPAddress);
                ReldatPacket syn = new ReldatPacket("".getBytes(), ReldatHeader.OPEN_FLAG, 0, 0);
                byte[] synBytes = syn.toBytes();
                DatagramPacket packet = new DatagramPacket(synBytes, synBytes.length, address, port);

                try {
                    this.outSocket.send(packet);

                    boolean kappa = false;
                    while (!kappa) {

                    }
                } catch (IOException e) {
                    System.out.println("IO");
                }

            } catch (UnknownHostException e) {
                System.out.println("kappa");
                return;
            }
        }
	}

	public void send( String data ) {
		// TODO
	}

	public String recv()
	{
		// TODO
		return null;
	}

	public void disconnect()
	{
		// TODO
	}
}
