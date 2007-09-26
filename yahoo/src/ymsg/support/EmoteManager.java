package ymsg.support;

import java.io.*;
import java.net.URL;
import java.util.*;

// *********************************************************************
// Loads and manipulates a custom emote file.  Emotes enable shortcut
// messages to be broadcast to a chat room, along the lines of "Waves" or
// "Winks".  It is also possible to mention a specific user in an Emote,
// although the message still goes to the entire room - for example "Waves
// at fred" or "Winks at jenny".  
//
// Each Emote therefore has a dual form, the 'room' form, and the 'user' 
// form.  In the user form, the string "%s" is used as a placeholder 
// for the name of the targeted user - for example "Waves at %s" when 
// targeted at "jenny" becomes "Waves at jenny".
// 
// This class holds a series of default Emotes internally.  When creating
// an instance, these defaults can either be included or ignored.  Any
// Emotes loaded by this class which share the same name as a default Emote
// will override the default.
//
// The standard file format for custom Yahoo Emotes is:
//   # Comment line
//   <name>\<room form>\<user form>
//
// For example:
//   # This is a winks Emote
//   Winks\Winks.\Winks at %s.
//   # This is a waves Emote
//   Waves\Waves at room.\Waves at %s.
// *********************************************************************
public class EmoteManager
{	private static Hashtable defaultRoom,defaultUser;

	private Hashtable room,user;
	private Vector errors,keys;
	
	// -----------------------------------------------------------------
	// STATIC CONSTRUCTOR - load the default Emotes from resource file
	// -----------------------------------------------------------------
	static
	{	defaultRoom = new Hashtable();  defaultUser = new Hashtable();
		Vector err = new Vector();
		try
		{	Class cls = new EmoteManager().getClass();
			loadEmoteData(cls.getResource("default_emotes").openStream(),defaultRoom,defaultUser,err);
		}catch(IOException e) { e.printStackTrace(); }
		for(int i=0;i<err.size();i++)
			System.err.println((String)err.elementAt(i));
	}
	// -----Why the hell isn't getClass() static?
	private EmoteManager() {}

	// -----------------------------------------------------------------
	// CONSTRUCTOR
	// If 'b' is true then the room and user hashes are initialised to
	// hold the contents of the static defaults, otherwise they are
	// initialised as empty.
	// -----------------------------------------------------------------
	private EmoteManager(boolean b)
	{	if(b)
		{	room = (Hashtable)defaultRoom.clone();
			user = (Hashtable)defaultUser.clone();
		}
		else
		{	room = new Hashtable();
			user = new Hashtable();
		}
		errors = new Vector();
	}

	// -----------------------------------------------------------------
	// CONSTRUCTOR - from file
	// -----------------------------------------------------------------
	public EmoteManager(File fl,boolean b)
	{	this(b);
		try
		{	loadEmoteData(new FileInputStream(fl),room,user,errors);
			setupKeys();
		}catch(IOException e) { errors.addElement(e.toString()); }
	}
	
	// -----------------------------------------------------------------
	// CONSTRUCTOR - from filename
	// -----------------------------------------------------------------
	public EmoteManager(String fn,boolean b)
	{	this(b);
		try
		{	loadEmoteData(new FileInputStream(fn),room,user,errors);
			setupKeys();
		}catch(IOException e) { errors.addElement(e.toString()); }
	}

	// -----------------------------------------------------------------
	// CONSTRUCTOR - from URL
	// -----------------------------------------------------------------
	public EmoteManager(URL u,boolean b)
	{	this(b);
		try
		{	loadEmoteData(u.openStream(),room,user,errors);
			setupKeys();
		}catch(IOException e) { errors.addElement(e.toString()); }
	}
	

	// -----------------------------------------------------------------
	// Utility method to load an arbitary Emote file
	// -----------------------------------------------------------------
	private static void loadEmoteData(InputStream ip,Hashtable rh,Hashtable uh,Vector err)
	throws IOException
	{	BufferedReader br = new BufferedReader(	new InputStreamReader(ip) );
		String in=br.readLine();
		int lineNo=0,i;
		while(in!=null)
		{	in=in.trim();  lineNo++;
			if(in.length()>0 && !in.startsWith("#"))
			{	try
				{	String n,r,u;
					i=in.indexOf("\\");  n=in.substring(0,i);  in=in.substring(i+1);
					i=in.indexOf("\\");  r=in.substring(0,i);  in=in.substring(i+1);
					u=in;
					rh.put(n,r);  uh.put(n,u);
				}
				catch(Exception e)
				{	if(err!=null)  err.addElement("Emote format error, line "+lineNo);
				}
			}
			in=br.readLine();
		}	
		br.close();
	}
	
	private void setupKeys()
	{	keys = new Vector();
		for(Enumeration e=room.keys();e.hasMoreElements();)
			keys.addElement(e.nextElement());
	}


	// -----------------------------------------------------------------
	// Returns the array of error strings while parsing emote data
	// -----------------------------------------------------------------
	public Vector getErrors() { return errors; }

	// -----------------------------------------------------------------
	// Get an emote, based upon name (id).
	// -----------------------------------------------------------------
	public String getRoomEmote(String name)
	{	return (String)room.get(name);
	}	
	public String getUserEmote(String name)
	{	return (String)user.get(name);
	}

	public Vector getNames() { return (Vector)keys.clone(); }

	// -----------------------------------------------------------------
	// Utility method to help combine room emote strings and usernames
	// -----------------------------------------------------------------
	public static String encodeEmote(String u,String em)
	{	if(u!=null)
		{	// -----Convert %s to 'u' user id (note: id could contain string "%s")
			StringBuffer result= new StringBuffer();
			int idx=em.indexOf("%s");
			while(idx>=0)
			{	result.append(em.substring(0,idx));  result.append(u);
				em=em.substring(idx+2);
				idx=em.indexOf("%s");
			}
			result.append(em);
			return result.toString();
		}
		else  return em;
	}
	
	
	public static void main(String[] args)
	{	EmoteManager em = new EmoteManager(args[0],true);
		Vector v = em.getNames();
		for(int i=0;i<v.size();i++)
		{	String n = (String)v.elementAt(i);
			System.out.println(n+": ["+em.getRoomEmote(n)+"] ["+em.getUserEmote(n)+"]");
		}
	}
}
