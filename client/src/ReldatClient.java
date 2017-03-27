import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import reldat.ReldatConnection;
import reldat.exception.HeaderCorruptedException;
import reldat.exception.PayloadCorruptedException;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;

public class ReldatClient {
	public static void main( String[] args ) throws IOException {
		if( args.length != 2 )
			usage();

		Pattern hostRegex = Pattern.compile( "^(\\d{1,3}[.]\\d{1,3}[.]\\d{1,3}[.]\\d{1,3}):(\\d{1,5})$" );
		Matcher hostMatch = hostRegex.matcher( args[0] );

		if( !hostMatch.matches() )
			usage();

		String ipAddress = hostMatch.group( 1 );
		int port         = Integer.parseInt( hostMatch.group( 2 ) );

		if( port > 65535 )
			usage();

		int maxReceiveWindowSize = Integer.parseInt( args[1] );

		ReldatConnection reldatConn = new ReldatConnection( maxReceiveWindowSize );
		reldatConn.connect( ipAddress, port );
		commandLoop( reldatConn );
		reldatConn.disconnect();
	}

	public static void usage() {
		System.out.println( "Usage: java ReldatClient <host IP address>:<host port> <max receive window size in packets>" );
		System.exit( 0 );
	}

	public static void commandLoop( ReldatConnection reldatConn ) throws IOException {
		boolean disconnect = false;
		Scanner scanner    = new Scanner( System.in );
		scanner.useDelimiter( "\n" );

		while( !disconnect ) {
			System.out.print( "> " );
			String clientInput = scanner.next();

			Pattern commandRegex = Pattern.compile( "(\\w+)\\s*(.+)?" );
			Matcher commandMatch = commandRegex.matcher( clientInput );

			if( commandMatch.matches() )
			{
				String command = commandMatch.group( 1 );

				switch( command )
				{
					case "disconnect":
						disconnect = true;
						break;
					case "transform":
						String filename = commandMatch.group( 2 );

						if( filename == null || filename.isEmpty() )
							System.out.println( "  Usage: transform <file>" );
						else
							transform( reldatConn, filename );
						break;
					case "sendMessage":
						//System.out.println("Working Directory = " + System.getProperty("user.dir"));
						String fileName = "./client/src/test_file.txt";
						String messageToSend = readFileToString(fileName); 
						reldatConn.send(messageToSend);
						break;
					case "receiveMessage":
						try {
							String kappa = reldatConn.recv();
							System.out.println(kappa);
						} catch (HeaderCorruptedException | PayloadCorruptedException e) {
							e.printStackTrace();
						} 
						break;
					default:
						System.out.println( "Unrecognized command. Valid commands are:\n    disconnect\n    transform" );
						break;
				}
			}
		}

		scanner.close();
	}

	public static void transform( ReldatConnection reldatConn, String filename ) {
        try {
			FileReader fr = new FileReader(filename);
            BufferedReader br = new BufferedReader(new FileReader(filename));

			String sCurrentLine = null;
            String newStr = "";

			while ((sCurrentLine = br.readLine()) != null) {
                newStr += sCurrentLine.toUpperCase();
			}

            System.out.println(newStr);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static String readFileToString(String filename) {
		String newStr = "";
        try {
			FileReader fr = new FileReader(filename);
            BufferedReader br = new BufferedReader(new FileReader(filename));

			String sCurrentLine = null;
            
			while ((sCurrentLine = br.readLine()) != null) {
                newStr += sCurrentLine;
			}
            System.out.println(newStr);
		} catch (IOException e) {
			e.printStackTrace();
		}
        return newStr;
	}
	
	
}
