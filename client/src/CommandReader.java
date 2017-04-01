import java.util.Scanner;
import java.util.concurrent.Semaphore;

public class CommandReader implements Runnable {
	private Scanner scanner;
	private volatile String cmd;
	private boolean closed;
	private Semaphore mutex;
	
	public CommandReader() {
		scanner = new Scanner( System.in );
		scanner.useDelimiter( "\n" );
		cmd = "";
		closed = false;
		mutex = new Semaphore(1);
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
		closed = true;
	}
}