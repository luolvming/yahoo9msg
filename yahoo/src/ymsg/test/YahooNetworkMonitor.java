package ymsg.test;

import java.io.*;
import java.net.*;

// This class is a kind of poor-man's Ethereal.  Run it and point a
// Yahoo client at it, and it will act as a proxy, forwarding all 
// traffic each way, but also dumping it to STDOUT.

public class YahooNetworkMonitor implements Runnable
{	ServerSocket socketListener;
	Connection toServer,toClient;

	private static final String HOST = "scs.msg.yahoo.com";
	private static final int PORT = 5050;
	private static final int LISTEN = 5128;

	// -------------------------------------------------------------
	// CONSTRUCTOR
	// -------------------------------------------------------------
	YahooNetworkMonitor()
	{	new Thread(this).start();
	}

	// -------------------------------------------------------------
	// Thread entry point.
	// -------------------------------------------------------------
	public void run()
	{	try
		{	System.out.println("Listening for client on "+LISTEN+" and directing to "+HOST+":"+PORT);
			socketListener = new ServerSocket(LISTEN);
			while(true)
			{	toClient = new Connection("S<<--C",socketListener.accept());
				toServer = new Connection("S-->>C",new Socket(HOST,PORT));
				toClient.setPipe(toServer);  toServer.setPipe(toClient);
				toClient.start();  toServer.start();
			}
		}catch(IOException e) { e.printStackTrace(); }
	}

	// -------------------------------------------------------------
	// Shutdown both I/O threads.
	// -------------------------------------------------------------
	public void shutdown() throws IOException
	{	toServer.stop();  toClient.stop();
	}


	private class Connection implements Runnable
	{	Socket socket;					// Our socket
		DataInputStream in;				// Our input stream
		DataOutputStream out;			// Our output stream

		String name;
		Thread thread;					// Input stream thread
		Connection pipe;				// Send incoming data here
		boolean quitFlag=false;			// Quit input thread

		Connection(String n,Socket s) throws IOException
		{	name=n;  socket=s;
			System.out.println("Connection ["+name+"]: "+socket.toString());
			in = new DataInputStream(socket.getInputStream());
			out = new DataOutputStream(socket.getOutputStream());
			thread = new Thread(this);
			thread.setDaemon(true);
		}

		void setPipe(Connection c) { pipe=c; }
		void start() { thread.start(); }
		void stop() throws IOException { quitFlag=true; socket.close(); }

		public void run()
		{	try
			{	byte[] buffer = new byte[10*1024];
				System.out.println("Thread ["+name+"] starting.");
				while(!quitFlag)
				{	int sz = in.read(buffer);
					pipe.out.write(buffer,0,sz);
					dump(buffer,sz,name);
				}
			}
			catch(IOException e)
			{	e.printStackTrace();
			}
			finally
			{	System.out.println("Thread ["+name+"] ending.");
				try { shutdown(); }catch(IOException e) { e.printStackTrace(); }
			}
		}
	}


	// -------------------------------------------------------------
	// Mutex lock used simply to prevent different outputs getting entangled
	// -------------------------------------------------------------
	synchronized static void dump(byte[] array,int sz)
	{	String s,c="";
		for(int i=0;i<sz;i++)
		{	s="0"+Integer.toHexString((int)array[i]);
			System.out.print(s.substring(s.length()-2)+" ");
			if((int)array[i]>=' ' && (int)array[i]<='~')  c=c+(char)array[i];
				else  c=c+".";
			if((i+1)==sz)
			{	while((i%20)!=19) { System.out.print("   ");  i++; }
			}
			if( (((i+1)%20)==0) || ((i+1)>=sz) )
			{	System.out.print(" "+c+"\n");  c="";
			}
		}
	}
	synchronized static void dump(byte[] array,int sz,String s)
	{	System.out.println(s+"\n01-02-03-04-05-06-07-08-09-10-11-12-13-14-15-16-17-18-19-20");
		dump(array,sz);
	}
	
	public static void main(String[] args)
	{	new YahooNetworkMonitor();
	}
}


