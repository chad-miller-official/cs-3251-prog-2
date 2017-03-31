import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import reldat.ReldatConnection;

public class ReldatClient {
	public static Semaphore mutex = new Semaphore(1);

	public static class CommandReader implements Runnable {
		Scanner scanner;
		volatile String cmd;
		boolean closed;
		
		public CommandReader() {
			scanner = new Scanner( System.in );
			scanner.useDelimiter( "\n" );
			cmd = "";
			closed = false;
		}

		@Override
		public void run() {
			while(!closed) {
				if(scanner.hasNext()) {
					try {
						mutex.acquire();
						cmd = scanner.next();
						mutex.release();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		public String getCommand()
		{
			String retval = cmd;
			cmd = "";
			return retval;
		}
		
		public void closeScanner()
		{
			scanner.close();
			closed = true;
		}
	}

	public static void main( String[] args ) throws IOException, InterruptedException {
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

	public static void commandLoop( ReldatConnection reldatConn ) throws IOException, InterruptedException {
		System.out.print( "> " );

		boolean disconnect = false;
		
		CommandReader cr = new CommandReader();
		Thread cmdInput  = new Thread(cr);
		cmdInput.start();

		while( !disconnect ) {
			String clientInput = cr.getCommand();
			
			Pattern commandRegex = Pattern.compile( "(\\w+)\\s*(.+)?" );
			Matcher commandMatch = commandRegex.matcher( clientInput );

			if( commandMatch.matches() )
			{
				String command = commandMatch.group( 1 );

				switch( command )
				{
					case "disconnect":
						disconnect = true;
						return;
					case "transform":
						//System.out.println("Working Directory = " + System.getProperty("user.dir"));
						String fileName = "./client/src/test_file.txt";
						String messageToSend = readFileToString(fileName); 
						System.out.println("TRANSFORMED DATA: " + reldatConn.conversation(messageToSend));
						break;
					default:
						System.out.println( "Unrecognized command " + command + ". Valid commands are:\n    disconnect\n    transform" );
						break;
				}
				
				System.out.print( "> " );
			}
			
			reldatConn.listen();
		}
		
		cr.closeScanner();
		cmdInput.join();
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
