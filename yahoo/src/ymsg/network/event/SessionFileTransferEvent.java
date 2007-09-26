package ymsg.network.event;

import java.net.URL;
import java.net.MalformedURLException;
import java.io.IOException;

// *********************************************************************
//							To		From	Message	Timestamp	Location
// fileTransferReceived		y		y		y		y			y
// *********************************************************************
public class SessionFileTransferEvent extends SessionEvent
{	protected URL location=null;

	// -----------------------------------------------------------------
	// CONSTRUCTORS
	// -----------------------------------------------------------------
	public SessionFileTransferEvent(Object o,String t,String f,String m,String dt,String l)
	{	super(o,t,f,m,dt);
		try { location = new URL(l); }
			catch(MalformedURLException e) { location=null; }
	}

	// -----------------------------------------------------------------
	// Accessors
	// -----------------------------------------------------------------
	public URL getLocation() { return location; }

	public String toString()
	{	return super.toString()+" location:"+location;
	}

	// -----------------------------------------------------------------
	// Unqualified name of file sent
	// -----------------------------------------------------------------
	public String getFilename()
	{	try
		{	String s = location.getFile();
			if(s.lastIndexOf("/")>0)  s=s.substring(s.lastIndexOf("/")+1);
			if(s.indexOf("?")>=0)  s=s.substring(0,s.indexOf("?"));
			return s;
		}catch(Exception e) { return "ymsg_default.out"; }
	}
}
