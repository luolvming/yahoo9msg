package ymsg.network;

import ymsg.network.event.*;
import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.*;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.util.*;

// *********************************************************************
// Written by FISH, Feb 2003 , Copyright FISH 2003 - 2005
//
// This class represents the main entry point into the YMSG9 API.  A
// Session represents one IM connection.
// *********************************************************************
public class Session implements StatusConstants, ServiceConstants, NetworkConstants
{	// -----Misc Yahoo data
	private String primaryID;				// Primary Yahoo ID: the real account id
	private String loginID;					// Login Yahoo ID: we logged in under this
	private String password;				// Yahoo user password
	private String cookieY,cookieT,cookieC; // Yahoo cookies (mmmm cOOOokies :)
	private String imvironment;				// IMvironment (decor, etc.)
	private long status;					// Yahoo status (available... etc)
	private String customStatusMessage;		// Message for custom status
	private boolean customStatusBusy;		// Available/Back=f, away=t
	private YahooGroup[] groups;			// Yahoo user's groups
	private YahooIdentity[] identities;		// Yahoo user's identities
	private int conferenceCount;			// Creating conference room names
	private UserStore userStore;			// Canonical (we hope) set of YahooUser's
	// -----Session
	private int sessionStatus;				// Status of session (see StatusConstants)
	private long sessionId=0;				// Holds Yahoo's session id
	private Vector listeners;				// Event listeners
	// -----I/O
	private ConnectionHandler network;		// Connection handler concrete
	// -----Threads
	private ThreadGroup ymsgThreads;		// Messenger threadgroup
	private InputThread ipThread;			// Async read from socket
	private PingThread pingThread;			// Send ping every 20 minutes
	private Hashtable typingNotifiers;		// Typing notification threads
	// -----Login
	private boolean loginOver=false;		// Marks start/end of logon process
	private YahooException loginException=null; // Exception created by login
	private YMSG9Packet cachePacket;		// For split packets in multiple parts
	// -----Conferences
	private Hashtable conferences;			// Current conferences, hashed on room
	// -----Chatrooms
	private boolean chatConnectOver=false; 	// Marks start/end of chatroom connection
	private boolean chatLoginOver=false; 	// Marks start/end of chatroom login
	private int chatSessionStatus;			// Chatroom session status
	private YahooChatLobby currentLobby=null; // Current chat lobby
	private String chatID;					// Current chat identity

	// -----------------------------------------------------------------
	// CONSTRUCTORS
	// -----------------------------------------------------------------
	public Session(ConnectionHandler ch)
	{	network=ch;  _init();
	}

	public Session()
	{	Properties p = System.getProperties();
		if(p.containsKey(SOCKS_HOST))
			network = new SOCKSConnectionHandler();
		else if(p.containsKey(PROXY_HOST) || p.containsKey(PROXY_HOST_OLD))
			network = new HTTPConnectionHandler();
		else
			network = new DirectConnectionHandler();
		_init();
	}

	private void _init()
	{	status=STATUS_AVAILABLE;  sessionId=0;  sessionStatus=UNSTARTED;
		ymsgThreads = new ThreadGroup("YMSG Threads");
		groups=null;  identities=null;
		listeners = new Vector();
		conferences = new Hashtable();  typingNotifiers = new Hashtable();
		userStore = new UserStore();

		network.install(this,ymsgThreads);
	}

	// -----------------------------------------------------------------
	// Event handler listeners
	// -----------------------------------------------------------------
	public void addSessionListener(SessionListener ss)
	{	if(listeners.indexOf(ss)<0)  listeners.addElement(ss);
	}

	public void removeSessionListener(SessionListener ss)
	{	listeners.removeElement(ss);
	}

	// -----------------------------------------------------------------
	// Returns the handler used to send/receive messages from the network
	// -----------------------------------------------------------------
	public ConnectionHandler getConnectionHandler() { return network; }

	// -----------------------------------------------------------------
	// Call this to connect to the Yahoo server and do all the initial
	// handshaking and accepting of data
	// u - Yahoo id
	// p - password
	// -----------------------------------------------------------------
	public synchronized void login(String u,String p)
	throws IllegalStateException,IOException,AccountLockedException,LoginRefusedException
	{	// -----Check the session status first
		if(sessionStatus!=UNSTARTED)
			throw new IllegalStateException("Session should be unstarted");

		// -----Yahoo ID's are apparently always lower case
		u=u.toLowerCase();

		// -----Reset session and init some variables
		resetData();
		loginID=u;  primaryID=null;  password=p;
		sessionId=0;  imvironment="0";
		try
		{	// -----Open the socket, create input and output streams
			network.open();

			// -----Create the socket input thread, begin the login process and
			// -----wait politely for its conclusion
			loginOver=false;
			startThreads();
			transmitAuth();
			// -----Wait until connection or timeout
			long timeout = System.currentTimeMillis()+Util.loginTimeout(LOGIN_TIMEOUT);
			//System.out.println("login1"+timeout);
			while(!loginOver && !past(timeout))
				try { Thread.sleep(10); } catch(InterruptedException e) {}
			// -----Check for failure
			System.out.println("login"+timeout);
			if(past(timeout))
			{	sessionStatus=FAILED;  closeNetwork();
				throw new InterruptedIOException("Login timed out");
			}
			else if(sessionStatus==FAILED && loginException!=null)
			{	/*if(loginException instanceof AccountLockedException)
					throw (AccountLockedException)loginException;
				else if(loginException instanceof LoginRefusedException)
					throw (LoginRefusedException)loginException; */
				throw (LoginRefusedException)loginException;
			}
		}
		finally
		{	// -----Logon failure?  When network input finishes, all
			// -----supporting threads are stopped and data members reset.
			/*if(sessionStatus != MESSAGING)  killInputThread();*/
		}
	}

	public synchronized void logout()
	throws IllegalStateException,IOException
	{	checkStatus();  sessionStatus=UNSTARTED;
		transmitLogoff();
	}

	// -----------------------------------------------------------------
	// Reset a failed session, so the session object can be used again
	// (for all those who like to minimise the number of discarded objects
	// for the GC to clean up  ;-)
	// -----------------------------------------------------------------
	public synchronized void reset() throws IllegalStateException
	{	if(sessionStatus!=FAILED && sessionStatus!=UNSTARTED)
			throw new IllegalStateException("Session currently active");
		sessionStatus=UNSTARTED;  chatSessionStatus=UNSTARTED;
		resetData();	// Just to be on the safe side
	}

	// -----------------------------------------------------------------
	// Send a message
	// to - the Yahoo ID to transmit to
	// msg - message text
	// -----------------------------------------------------------------
	public void sendMessage(String to,String msg)
	throws IllegalStateException,IOException
	{	checkStatus();  transmitMessage(to,loginID,msg);
	}
	public void sendMessage(String to,String msg,YahooIdentity yid)
	throws IllegalStateException,IOException,IllegalIdentityException
	{	checkStatus();  checkIdentity(yid);  transmitMessage(to,yid.getId(),msg);
	}

	// -----------------------------------------------------------------
	// Send a >>buzz<< message
	// -----------------------------------------------------------------
	public void sendBuzz(String to)
	throws IllegalStateException,IOException
	{	sendMessage(to,BUZZ);		// Buzz defined in NetworkConstants
	}
	public void sendBuzz(String to,YahooIdentity yid)
	throws IllegalStateException,IOException,IllegalIdentityException
	{	sendMessage(to,BUZZ,yid);	// Buzz defined in NetworkConstants
	}


	// -----------------------------------------------------------------
	// Get the status of the session, ie: unstarted, authenticating, etc.
	// Legit values are in the StatusConstants interface.  Check this
	// after login() to find out if you've connected to Yahoo okay.
	// -----------------------------------------------------------------
	public int getSessionStatus() { return sessionStatus; }

	// -----------------------------------------------------------------
	// Get/set the Yahoo status, ie: available, invisible, busy, not at
	// desk, etc.  Legit values are in the StatusConstants interface.
	// If you want to login as invisible, set this to STATUS_INVISIBLE
	// before you call login()
	// Note: setter is overloaded, the second version is intended for
	// use when setting custom status messages.  The boolean is true if
	// available and false if away.
	// -----------------------------------------------------------------
	public long getStatus() { return status; }

	public synchronized void setStatus(long s)
	throws IllegalArgumentException,IOException
	{	if(sessionStatus==UNSTARTED && !(s==STATUS_AVAILABLE || s==STATUS_INVISIBLE))
			throw new IllegalArgumentException("Unstarted sessions can be available or invisible only");
		if(s==STATUS_CUSTOM)
			throw new IllegalArgumentException("Cannot set custom state without message");
		status=s;  customStatusMessage=null;
		if(sessionStatus==MESSAGING)  _doStatus();
	}

	public synchronized void setStatus(String m,boolean b)
	throws IllegalArgumentException,IOException
	{	if(sessionStatus==UNSTARTED)
			throw new IllegalArgumentException("Unstarted sessions can be available or invisible only");
		if(m==null)  throw new IllegalArgumentException("Cannot set custom state with null message");
		status=STATUS_CUSTOM;
		customStatusMessage=m;  customStatusBusy=b;  _doStatus();
	}

	private void _doStatus() throws IllegalStateException,IOException
	{	if(status==STATUS_AVAILABLE)  transmitIsBack();
		else if(status==STATUS_CUSTOM)  transmitIsAway(customStatusMessage,customStatusBusy);
		else  transmitIsAway();
	}

	public String getCustomStatusMessage() { return customStatusMessage; }
	public boolean isCustomBusy() { return customStatusBusy; }

	// -----------------------------------------------------------------
	// Ask server to return refreshed stats for this session.  Server will 
	// send back a USERSTAT and truncated NEWMAIL packet.
	// -----------------------------------------------------------------
	public void refreshStats()
	throws IllegalStateException,IOException
	{	checkStatus();  transmitUserStat();
	}

	// -----------------------------------------------------------------
	// Identity code.  Deals with Yahoo's alias system which allows 
	// multiple identities per user.  Primary identity is the *real* 
	// (original) identity, the login identity is the one the session was 
	// logged in under, and as such acts as the default for overloaded
	// methods without an identity parameter.
	// -----------------------------------------------------------------
	public YahooIdentity[] getIdentities()
	{	if(identities==null)  return null;
			else  return (YahooIdentity[])identities.clone();
	}

	public YahooIdentity getPrimaryIdentity() { return identityIdToObject(primaryID); }
	public YahooIdentity getLoginIdentity() { return identityIdToObject(loginID); }

	public void activateIdentity(YahooIdentity yid,boolean activate)
	throws IllegalStateException, IllegalIdentityException, IOException
	{	checkStatus();  checkIdentity(yid);
		// -----Make an exception of the primary identity
		if(yid.getId().equals(primaryID))
			throw new IllegalIdentityException("Primary identity cannot be de/activated");
		// -----Send message
		if(activate)  transmitIdActivate(yid.getId());
			else  transmitIdDeactivate(yid.getId());
	}

	// -----------------------------------------------------------------
	// Add/remove source AWT text component to use for TYPING packets sent
	// to the specified user, from a given identity.  Each notifier is tied
	// to both target and source.  Note: this method has now been changed so
	// it no longer needs an AWT component.  'com' can be null, with the
	// API user employing keyTyped() to manually send key strokes.
	//
	// There's serious bug in Yahoo which means that even if you send a
	// typing notify packet with an alternate identity in it, Yahoo always
	// delivers a packet with the primary id.  (Security/privacy bug?)  For
	// this reason these methods do not *yet* support id's - however, as
	// you'll see, the code is all ready for an extra 'syid' parameter.
	// -----------------------------------------------------------------
	public void addTypingNotification(String user,Component com)
	{	/*if(syid==null)  syid=loginID*/
		String syid=primaryID;
		String key="user"+"\n"+syid;
		synchronized(typingNotifiers)
		{	if(typingNotifiers.containsKey(key)) return;  // Aleady registered
			typingNotifiers.put(key,new TypingNotifier(com,user,syid));
		}
	}

	public void removeTypingNotification(String user)
	{	/*if(syid==null)  syid=loginID*/
		String syid=primaryID;
		String key="user"+"\n"+syid;
		synchronized(typingNotifiers)
		{	TypingNotifier tn = (TypingNotifier)typingNotifiers.get(key);
			if(tn==null)  return;
			tn.quit=true;  tn.interrupt();
			typingNotifiers.remove(key);
		}
	}

	public void keyTyped(String user)
	{	/*if(syid==null)  yid=loginID*/
		String syid=primaryID;
		String key="user"+"\n"+syid;
		TypingNotifier tn = (TypingNotifier)typingNotifiers.get(key);
		if(tn!=null)  tn.keyTyped();
	}

	// -----------------------------------------------------------------
	// Return lists for friends tree menu
	// -----------------------------------------------------------------
	public YahooGroup[] getGroups() { return (YahooGroup[])groups.clone(); }
	public Hashtable getUsers() { return (Hashtable)userStore.getUsers().clone(); }
	public YahooUser getUser(String id) { return userStore.get(id); }

	// -----------------------------------------------------------------
	// General accessors
	// -----------------------------------------------------------------
	public String getImvironment() { return imvironment; }

	public String[] getCookies()
	{	String[] arr = new String[3];
		arr[COOKIE_Y]=cookieY;  arr[COOKIE_T]=cookieT;  arr[COOKIE_C]=cookieC;
		return arr;
	}

	// -----------------------------------------------------------------
	// Conference code
	// -----------------------------------------------------------------
	public YahooConference createConference(String[] users,String msg)
	throws IllegalStateException,IOException,IllegalIdentityException
	{	checkIdentityNotOnList(users);
		return createConference(users,msg,identityIdToObject(loginID));
	}

	public YahooConference createConference(String[] users,String msg,YahooIdentity yid)
	throws IllegalStateException,IOException, IllegalIdentityException
	{	checkStatus();  checkIdentity(yid);  checkIdentityNotOnList(users);
		String r = getConferenceName(yid.getId());
		transmitConfInvite(users,yid.getId(),r,msg);
		try { return getConference(r); }
			catch(NoSuchConferenceException e) { return null; }
	}

	public void acceptConferenceInvite(YahooConference room)
	throws IllegalStateException,IOException,NoSuchConferenceException
	{	checkStatus();  transmitConfLogon(room.getName(),room.getIdentity().getId());
	}

	public void declineConferenceInvite(YahooConference room,String msg)
	throws IllegalStateException,IOException,NoSuchConferenceException
	{	checkStatus();  transmitConfDecline(room.getName(),room.getIdentity().getId(),msg);
	}

	public void extendConference(YahooConference room,String user,String msg)
	throws IllegalStateException,IOException,NoSuchConferenceException,IllegalIdentityException
	{	checkStatus();
		String[] arr = { user };  checkIdentityNotOnList(arr);
		transmitConfAddInvite(user,room.getName(),room.getIdentity().getId(),msg);
	}

	public void sendConferenceMessage(YahooConference room,String msg)
	throws IllegalStateException,IOException,NoSuchConferenceException
	{	checkStatus();  transmitConfMsg(room.getName(),room.getIdentity().getId(),msg);
	}

	public void leaveConference(YahooConference room)
	throws IllegalStateException,IOException,NoSuchConferenceException
	{	checkStatus();  transmitConfLogoff(room.getName(),room.getIdentity().getId());
	}

	// -----------------------------------------------------------------
	// Friends code
	// -----------------------------------------------------------------
	public void addFriend(String friend,String group)
	throws IllegalStateException,IOException
	{	checkStatus();  transmitFriendAdd(friend,group);
	}

	public void removeFriend(String friend,String group)
	throws IllegalStateException,IOException
	{	checkStatus();  transmitFriendRemove(friend,group);
	}

	public void rejectContact(SessionEvent se,String msg)
	throws IllegalArgumentException,IllegalStateException,IOException
	{	if(se.getFrom()==null || se.getTo()==null)
			throw new IllegalArgumentException("Missing to or from field in event object.");
		checkStatus();  transmitContactReject(se.getFrom(),se.getTo(),msg);
	}

	public void ignoreContact(String friend,boolean ignore)
	throws IllegalStateException,IOException
	{	checkStatus();  transmitContactIgnore(friend,ignore);
	}

	public void refreshFriends()
	throws IllegalStateException,IOException
	{	checkStatus();  transmitList();
	}

	// -----------------------------------------------------------------
	// File transfer
	// 'save as' saves to a particular directory and filename, 'save to'
	// uses the file's own name and saves it to a particular directory.
	// Note: the 'to' method gets its filename from different sources.
	// Initially the URL filename (minus the path), however the header
	// Content-Disposition will override this.  The 'as' method always uses
	// its own specified filename.  If both _path_ and _filename_ are not
	// null then the saveFT() method assumes 'to'... but if _path_ is null,
	// saveFT() assumes 'as' and _filename_ is the entire path+filename.
	// -----------------------------------------------------------------
	public void sendFileTransfer(String user,String filename,String msg)
	throws IllegalStateException,FileTransferFailedException,IOException
	{	checkStatus();  transmitFileTransfer(user,msg,filename);
	}

	public void saveFileTransferAs(SessionFileTransferEvent ev,String filename)
	throws FileTransferFailedException,IOException
	{	saveFT(ev,null,filename);
	}

	public void saveFileTransferTo(SessionFileTransferEvent ev,String dir)
	throws FileTransferFailedException,IOException
	{	// -----Yahoo encodes the filename into the URL, but allow for
		// -----Content-Disposition header override.
		if(!dir.endsWith(File.separator))  dir=dir+File.separator;
		saveFT(ev,dir,ev.getFilename());
	}

	private void saveFT(SessionFileTransferEvent ev,String path,String filename)
	throws FileTransferFailedException,IOException
	{	int len;
		byte[] buff = new byte[4096];
		String contDisp = "Content-Disposition: filename=";

		// -----HTTP request
		HTTPConnection conn = new HTTPConnection("GET",ev.getLocation());
		conn.println("Host: "+ev.getLocation().getHost());
		conn.println("User-Agent: "+USER_AGENT);
		conn.println("Cookie: "+cookieY+"; "+cookieT);
		conn.println("");
		conn.flush();

		// -----Response header
		String in = conn.readLine();
		if(in.indexOf(" 200 ")<0)  throw new FileTransferFailedException("Server HTTP error code: "+in);
		do
		{	in=conn.readLine();
			// -----Change the filename if C-D. header?
			if(path!=null && in!=null && in.startsWith(contDisp))
			{	filename=in.substring(contDisp.length());
				// -----Strip quotes if necessary
				if(filename.charAt(0)=='\"')
					filename=filename.substring(1,filename.length()-1);
			}
		}while(in!=null && in.trim().length()>0);
		if(in==null)  throw new FileTransferFailedException("Server premature end of reply");
		// -----Response body
		if(path!=null)  filename=path+filename;
		DataOutputStream dos = new DataOutputStream(new FileOutputStream(filename));
		do
		{	len = conn.read(buff);
			if(len>0)  dos.write(buff,0,len);
		}while(len>=0);
		dos.flush();  dos.close();
		conn.close();
	}

	// -----------------------------------------------------------------
	// Chatroom code
	// -----------------------------------------------------------------
	public synchronized void chatLogin(YahooChatLobby ycl)
	throws IllegalStateException,IOException,LoginRefusedException
	{	chatLogin(ycl,identityIdToObject(loginID));
	}

	public synchronized void chatLogin(YahooChatLobby ycl,YahooIdentity yid)
	throws IllegalStateException,IOException,LoginRefusedException,IllegalIdentityException
	{	// -----The regular session should be logged in, and the chat status
		// -----should be unstarted or messaging
		checkStatus();  checkIdentity(yid);
		if(chatSessionStatus!=UNSTARTED && chatSessionStatus!=MESSAGING)
			throw new IllegalStateException("Chat session should be unstarted or messaging");

		chatConnectOver=false;  chatLoginOver=false;  chatID=yid.getId();

		try
		{	// -----Transmit 'connect' packet and wait for acknowledgement
			long timeout = System.currentTimeMillis()+Util.loginTimeout(LOGIN_TIMEOUT);
			if(currentLobby==null)
			{	transmitChatConnect(yid.getId());
				while(!chatConnectOver && !past(timeout))
					try { Thread.sleep(10); } catch(InterruptedException e) {}
				if(past(timeout))
					throw new InterruptedIOException("Chat connect timed out");
			}
			// -----Transmit 'login' packet and wait for acknowledgement
			transmitChatLogon(ycl.getNetworkName(),ycl.getParent().getId());
			while(!chatLoginOver && !past(timeout))
				try { Thread.sleep(10); } catch(InterruptedException e) {}
			if(past(timeout))
				throw new InterruptedIOException("Chat login timed out");
			else if(chatSessionStatus==FAILED && loginException!=null)
				throw (LoginRefusedException)loginException;

			// -----Successful?
			if(chatSessionStatus==MESSAGING) currentLobby=ycl;
				else  currentLobby=null;
		}
		finally
		{	if(chatSessionStatus!=MESSAGING)
			{	chatSessionStatus=FAILED;  chatID=null;
			}
		}
	}

	public synchronized void chatLogout()
	throws IllegalStateException,IOException
	{	checkStatus();  checkChatStatus();
		transmitChatDisconnect(currentLobby.getNetworkName());
		currentLobby=null;
	}

	public void sendChatMessage(String msg)
	throws IllegalStateException,IOException
	{	checkStatus();  checkChatStatus();
		transmitChatMsg(currentLobby.getNetworkName(),msg,false);
	}

	public void sendChatEmote(String emote)
	throws IllegalStateException,IOException
	{	checkStatus();  checkChatStatus();
		transmitChatMsg(currentLobby.getNetworkName(),emote,true);
	}


	public YahooChatLobby getCurrentChatLobby() { return currentLobby; }

	public int getChatSessionStatus() { return chatSessionStatus; }

	public void resetChat() throws IllegalStateException
	{	if(chatSessionStatus!=FAILED && chatSessionStatus!=UNSTARTED)
			throw new IllegalStateException("Chat session currently active");
		chatSessionStatus=UNSTARTED;
	}

	// -----------------------------------------------------------------
	// Test - ignore these (used as hooks for test client)
	// -----------------------------------------------------------------
	public void __test1(String a1,String a2)
	{	try { network.close(); }catch(IOException e) {}
		//try { transmitChatPM(a1,a2); }
		//	catch(Exception e) { e.printStackTrace(); }
	}
	public void __test2()
	{
	}


	// -----------------------------------------------------------------
	// Transmit an AUTH packet, as a way of introduction to the server.
	// As we do not know our primary ID yet, both 0 and 1 use loginID .
	// -----------------------------------------------------------------
	protected void transmitAuth() throws IOException
	{	sessionStatus=AUTH;  				// Set status
		PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("0",loginID);		// FIX: only req. for HTTPConnectionHandler ?
		body.addElement("1",loginID);
		sendPacket(body,SERVICE_AUTH);						// 0x57
	}

	// -----------------------------------------------------------------
	// Transmit an AUTHRESP packet, the second part of our login process.
	// As we do not know our primary ID yet, both 0 and 1 use loginID .
	// Note: message also contains our initial status.
	// plp - plain response (not MD5Crypt'd)
	// crp - crypted response (MD5Crypt'd)
	// -----------------------------------------------------------------
	protected void transmitAuthResp(String plp,String crp) throws IOException
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("0" ,loginID);
		body.addElement("6" ,plp);
		body.addElement("96",crp);
		body.addElement("2" ,"1");
		body.addElement("1" ,loginID);
		sendPacket(body,SERVICE_AUTHRESP,status);			// 0x54
	}

	// -----------------------------------------------------------------
	// Transmit a CHATCONNECT packet.  We send one of these as the first
	// packet to the chat server - by way of introduction.  The server
	// responds with its own 0x96 packet back at us, and then we can logon.
	// -----------------------------------------------------------------
	protected void transmitChatConnect(String yid) throws IOException
	{	chatSessionStatus=CONNECT;					// Set status
		PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("109",primaryID);
		body.addElement("1",yid);
		body.addElement("6","abcde");				// FIX: what is this?
		sendPacket(body,SERVICE_CHATCONNECT);		// 0x96
	}

	// -----------------------------------------------------------------
	// Transmit a CHATDISCONNECT packet.
	// -----------------------------------------------------------------
	protected void transmitChatDisconnect(String room) throws IOException
	{	chatSessionStatus=UNSTARTED;  currentLobby=null;
		PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("104",room);
		body.addElement("109",chatID);
		sendPacket(body,SERVICE_CHATDISCONNECT);	// 0xa0
	}

	// -----------------------------------------------------------------
	// Transmit a CHATLOGON packet.  We send one of these after the
	// CHATCONNECT packet, as the second phase of chat login.  Note:
	// netname uses network name of "room:lobby".
	// -----------------------------------------------------------------
	protected void transmitChatLogon(String netname,long id) throws IOException
	{	chatSessionStatus=LOGON;					// Set status
		PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("1",chatID);
		body.addElement("104",netname);
		body.addElement("129",""+id);
		body.addElement("62","2");					// FIX: what is this?
		sendPacket(body,SERVICE_CHATLOGON);					// 0x98
	}

	// -----------------------------------------------------------------
	// Transmit a CHATMSG packet.  The contents of this message will be
	// forwarded to other users of the chatroom, BUT NOT TO US!  Note:
	// 'netname' is the network name of "room:lobby".
	// -----------------------------------------------------------------
	protected void transmitChatMsg(String netname,String msg,boolean emote) throws IOException
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("1",chatID);
		body.addElement("104",netname);
		body.addElement("117",msg);
		if(emote)  body.addElement("124","2");		// 1=Regular, 2=Emote
			else  body.addElement("124","1");
		if(Util.isUtf8(msg))  body.addElement("97","1");
		sendPacket(body,SERVICE_CHATMSG);					// 0xa8
	}

	// -----------------------------------------------------------------
	// Transmit a CHATPM packet.  Person message packets.
	// ************ DOES THIS WORK ???
	// -----------------------------------------------------------------
	protected void transmitChatPM(String to,String msg) throws IOException
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("5",to);
		body.addElement("14",msg);
		sendPacket(body,SERVICE_CHATPM);					// 0x020
	}

	// -----------------------------------------------------------------
	// Transmit a CHATPING packet.
	// -----------------------------------------------------------------
	protected void transmitChatPing() throws IOException
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		sendPacket(body,SERVICE_CHATPING);					// 0xa1
	}

	// -----------------------------------------------------------------
	// Transmit an CONFADDINVITE packet.  We send one of these when we
	// wish to invite more users to our conference.
	// -----------------------------------------------------------------
	protected void transmitConfAddInvite(String user,String room,String yid,String msg)
	throws IOException,NoSuchConferenceException
	{	// -----Check this conference actually exists (throws exception if not)
		getConference(room);
		// -----Send new invite packet to Yahoo
		PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("1",yid);
		body.addElement("51",user);
		body.addElement("57",room);
		Vector users = getConference(room).getUsers();
		for(int i=0;i<users.size();i++)
			body.addElement("52",((YahooUser)users.elementAt(i)).getId());
		for(int i=0;i<users.size();i++)
			body.addElement("53",((YahooUser)users.elementAt(i)).getId());
		body.addElement("58",msg);
		body.addElement("13","0");  // FIX : what's this for?
		sendPacket(body,SERVICE_CONFADDINVITE);				// 0x1c
	}

	// -----------------------------------------------------------------
	// Transmit an CONFDECLINE packet.  We send one of these when we
	// decline an offer to join a conference.
	// -----------------------------------------------------------------
	protected void transmitConfDecline(String room,String yid,String msg)
	throws IOException,NoSuchConferenceException
	{	// -----Flag this conference as now dead
		YahooConference yc = getConference(room);  yc.closeConference();
		Vector users = yc.getUsers();
		// -----Send decline packet to Yahoo
		PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("1",yid);
		for(int i=0;i<users.size();i++)
			body.addElement("3",((YahooUser)users.elementAt(i)).getId());
		body.addElement("57",room);
		body.addElement("14",msg);
		sendPacket(body,SERVICE_CONFDECLINE);				// 0x1a
	}

	// -----------------------------------------------------------------
	// Transmit an CONFINVITE packet.  This is sent when we want to
	// create a new conference, with the specified users and with a
	// given welcome message.
	// -----------------------------------------------------------------
	protected void transmitConfInvite(String[] users,String yid,String room,String msg) throws IOException
	{	// -----Create a new conference object
		conferences.put(room,new YahooConference(userStore,identityIdToObject(yid),room,this,false));
		// -----Send request to Yahoo
		PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("1",yid);
		body.addElement("50",primaryID);
		for(int i=0;i<users.length;i++)  body.addElement("52",users[i]);
		body.addElement("57",room);
		body.addElement("58",msg);
		body.addElement("13","0");  // FIX: what's this for?
		sendPacket(body,SERVICE_CONFINVITE);				// 0x18
	}

	// -----------------------------------------------------------------
	// Transmit an CONFLOGOFF packet.  We send one of these when we wish
	// to leave a conference.
	// -----------------------------------------------------------------
	protected void transmitConfLogoff(String room,String yid)
	throws IOException,NoSuchConferenceException
	{	// -----Flag this conference as now dead
		YahooConference yc = getConference(room);  yc.closeConference();
		Vector users = yc.getUsers();
		// -----Send decline packet to Yahoo
		PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("1",yid);
		for(int i=0;i<users.size();i++)
			body.addElement("3",((YahooUser)users.elementAt(i)).getId());
		body.addElement("57",room);
		sendPacket(body,SERVICE_CONFLOGOFF);				// 0x1b
	}

	// -----------------------------------------------------------------
	// Transmit an CONFLOGON packet.  Send this when we want to accept
	// an offer to join a conference.
	// -----------------------------------------------------------------
	protected void transmitConfLogon(String room,String yid)
	throws IOException,NoSuchConferenceException
	{	// -----Get a list of users for this conference
		Vector users = getConference(room).getUsers();
		// -----Send accept packet to Yahoo
		PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("1",yid);
		for(int i=0;i<users.size();i++)
			body.addElement("3",((YahooUser)users.elementAt(i)).getId());
		body.addElement("57",room);
		sendPacket(body,SERVICE_CONFLOGON);					// 0x19
	}

	// -----------------------------------------------------------------
	// Transmit an CONFMSG packet.  We send one of these when we wish
	// to send a message to a conference.
	// -----------------------------------------------------------------
	protected void transmitConfMsg(String room,String yid,String msg)
	throws IOException,NoSuchConferenceException
	{	// -----Get a list of users for this conference
		Vector users = getConference(room).getUsers();
		// -----Send message packet to yahoo
		PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("1",yid);
		for(int i=0;i<users.size();i++)
			body.addElement("53",((YahooUser)users.elementAt(i)).getId());
		body.addElement("57",room);
		body.addElement("14",msg);
		if(Util.isUtf8(msg))  body.addElement("97","1");
		sendPacket(body,SERVICE_CONFMSG);					// 0x1d
	}

	// -----------------------------------------------------------------
	// Transmit an CONTACTIGNORE packet.  We would do this in response to
	// an ADDFRIEND packet arriving. (???)
	// FIX: when does this get sent?
	// -----------------------------------------------------------------
	protected void transmitContactIgnore(String friend,boolean ignore) throws IOException
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("1" ,primaryID); 	// FIX: effective id?
		body.addElement("7" ,friend);
		if(ignore)	body.addElement("13","1");  // Bug: 1/2 not 0/1 ???
			else  body.addElement("13","2");
		sendPacket(body,SERVICE_CONTACTIGNORE);				// 0x85
	}

	// -----------------------------------------------------------------
	// Transmit a CONTACTREJECT packet.  We would do this when we wish
	// to overrule an attempt to add us as a friend (when we get a
	// ADDFRIEND packet!)
	// -----------------------------------------------------------------
	protected void transmitContactReject(String friend,String yid,String msg) throws IOException
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("1" ,yid);
		body.addElement("7" ,friend);
		body.addElement("14",msg);
		sendPacket(body,SERVICE_CONTACTREJECT);				// 0x86
	}

	// -----------------------------------------------------------------
	// Transmit a FILETRANSFER packet, to send a binary file to a friend.
	// -----------------------------------------------------------------
	protected void transmitFileTransfer(String to,String message,String filename)
	throws FileTransferFailedException,IOException
	{	String cookie = cookieY+"; "+cookieT;
		int fileSize=-1;
		byte[] packet;
		byte[] marker = { '2','9',(byte)0xc0,(byte)0x80 };

		// -----Load binary from file
		DataInputStream dis = new DataInputStream(new FileInputStream(filename));
		fileSize=dis.available();
		if(fileSize<=0)
			throw new FileTransferFailedException("File transfer: missing or empty file");
		byte[] fileData = new byte[fileSize];
		dis.readFully(fileData);  dis.close();

		// -----Create a Yahoo packet into 'packet'
		PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("0" ,primaryID);
		body.addElement("5" ,to);
		body.addElement("28",fileSize+"");
		body.addElement("27",new File(filename).getName());
		body.addElement("14",message);
		packet = body.getBuffer();

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(baos);
		dos.write(MAGIC,0,4);  dos.write(VERSION,0,4);
		dos.writeShort((packet.length+4) & 0xFFFF);
		dos.writeShort(SERVICE_FILETRANSFER & 0xFFFF);
		dos.writeInt((int)(status & 0xFFFFFFFF));
		dos.writeInt((int)(sessionId & 0xFFFFFFFF));
		dos.write(packet,0,packet.length);
		dos.write(marker,0,4);  // Extra 4 bytes : marker before file data (?)

		packet = baos.toByteArray();

		// -----Send to Yahoo using POST
		String ftHost = Util.fileTransferHost();
		String ftURL = "http://"+ftHost+FILE_TF_PORTPATH;
		HTTPConnection conn = new HTTPConnection("POST",new URL(ftURL));
		conn.println("Content-Length: "+(fileSize+packet.length));
		conn.println("User-Agent: "+USER_AGENT);
		conn.println("Host: "+ftHost);
		conn.println("Cookie: "+cookie);
		conn.println("");
		conn.write(packet);									// 0x46
		conn.write(fileData);
		conn.flush();

		// -----Read HTTP header
		String in = conn.readLine() , head=in;
		if(in!=null)
		{	byte[] buffer = new byte[4096];			// FIX: this code just gobbles
			while(conn.read(buffer)>0);				// bytes - should read and parse!
		}
		/*while(in!=null && in.trim().length>0)  in=conn.readLine();
		// -----Body
		byte[] buff = new byte[4];
		int len = conn.read(buff);
		String packHead=="";
		if(len>0 && buff[0]>0)
		{	len = conn.read(buff);  // YHOO=fail, YMSG=success (?)
			packHead=(char)buff[0]+(char)buff[1]+(char)buff[2]+(char)buff[3];
		}  // FIX - should read rest of header
		*/
		conn.close();
		if(head.indexOf(" 200 ")<0)
			throw new FileTransferFailedException("Server rejected upload");
	}

	// -----------------------------------------------------------------
	// Transmit a FRIENDADD packet.  If all goes well we'll get a
	// FRIENDADD packet back with the details of the friend to confirm
	// the transation (usually preceeded by a CONTACTNEW packet with
	// well detailed info).
	// friend - Yahoo id of friend to add
	// group - Group to add it to
	// -----------------------------------------------------------------
	protected void transmitFriendAdd(String friend,String group) throws IOException
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("1" ,primaryID); 	// ???: effective id?
		body.addElement("7" ,friend);
		body.addElement("65",group);
		sendPacket(body,SERVICE_FRIENDADD);					// 0x83
	}

	// -----------------------------------------------------------------
	// Transmit a FRIENDREMOVE packet.  We should get a FRIENDREMOVE
	// packet back (usually preceeded by a CONTACTNEW packet).
	// friend - Yahoo id of friend to remove
	// group - Group to remove it from
	// -----------------------------------------------------------------
	protected void transmitFriendRemove(String friend,String group) throws IOException
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("1" ,primaryID);	// ???: effective id?
		body.addElement("7" ,friend);
		body.addElement("65",group);
		sendPacket(body,SERVICE_FRIENDREMOVE);				// 0x84
	}

	// -----------------------------------------------------------------
	// Transmit a GROUPRENAME packet, to change the name of one of our
	// friends groups.
	// -----------------------------------------------------------------
	protected void transmitGroupRename(String oldName,String newName) throws IOException
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("1" ,primaryID);	// ???: effective id?
		body.addElement("65",oldName);
		body.addElement("67",newName);
		sendPacket(body,SERVICE_GROUPRENAME);				// 0x13
	}

	// -----------------------------------------------------------------
	// Transmit a IDACT packet.
	// -----------------------------------------------------------------
	protected void transmitIdActivate(String id) throws IOException
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("3",id);
		sendPacket(body,SERVICE_IDACT);						// 0x07
	}

	// -----------------------------------------------------------------
	// Transmit a IDDEACT packet.
	// -----------------------------------------------------------------
	protected void transmitIdDeactivate(String id) throws IOException
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("3",id);
		sendPacket(body,SERVICE_IDDEACT);					// 0x08
	}

	// -----------------------------------------------------------------
	// Transmit an IDLE packet.  Used by the HTTP proxy connection to
	// provide a mechanism were by incoming packets can be delivered.  An
	// idle packet is sent every thirty seconds (as part of a HTTP POST)
	// and the server responds with all the packets accumulated since last
	// contact.
	// -----------------------------------------------------------------
	protected void transmitIdle() throws IOException
	{	PacketBodyBuffer body =  new PacketBodyBuffer();
		body.addElement("1",loginID);		// FIX: Should this be primary?
		body.addElement("0",primaryID);
		sendPacket(body,SERVICE_IDLE);						// 0x05
	}

	// -----------------------------------------------------------------
	// Transmit an ISAWAY packet.  To return, try transmiting an ISBACK
	// packet!  Comes in two flavours: custom message and regular
	// -----------------------------------------------------------------
	protected void transmitIsAway() throws IOException
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("10",status+"");
		sendPacket(body,SERVICE_ISAWAY,status);				// 0x03
	}

	protected void transmitIsAway(String msg,boolean a) throws IOException
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		status=STATUS_CUSTOM;
		body.addElement("10",status+"");
		body.addElement("19",msg);
		if(a) body.addElement("47","1");  // 1=away
			else body.addElement("47","0");  // 0=back
		sendPacket(body,SERVICE_ISAWAY,status);				// 0x03
	}

	// -----------------------------------------------------------------
	// Transmit an ISBACK packet, contains no body, just the Yahoo status.
	// We should send this to return from an ISAWAY, or after we have
	// confirmed a sucessful LOGON - it sets our initial status (visibility)
	// to the outside world.  Typical initial values for 'status' are
	// AVAILABLE and INVISIBLE.
	// -----------------------------------------------------------------
	protected void transmitIsBack() throws IOException
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("10",status+"");
		sendPacket(body,SERVICE_ISBACK,status);				// 0x04
	}

	// -----------------------------------------------------------------
	// Transmit a LIST packet.
	// -----------------------------------------------------------------
	protected void transmitList() throws IOException
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("1",primaryID);
		sendPacket(body,SERVICE_LIST);						// 0x55
	}

	// -----------------------------------------------------------------
	// Transmit a LOGOFF packet, which should exit us from Yahoo IM.
	// -----------------------------------------------------------------
	protected void transmitLogoff() throws IOException
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("0" ,loginID);			// Is this only in for HTTP?
		sendPacket(body,SERVICE_LOGOFF);					// 0x02
	}

	// -----------------------------------------------------------------
	// Transmit a MESSAGE packet.
	// to - the Yahoo ID of the user to send the message to
	// yid - Yahoo identity
	// msg - the text of the message
	// -----------------------------------------------------------------
	protected void transmitMessage(String to,String yid,String msg) throws IOException
	{	// -----Send packet
		PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("0" ,primaryID);	// From (primary ID)
		body.addElement("1" ,yid);			// From (effective ID)
		body.addElement("5" ,to);			// To
		body.addElement("14",msg);			// Message
		// -----Extension for YMSG9
		if(Util.isUtf8(msg))  body.addElement("97","1");
		body.addElement("63",";"+imvironment); // Not supported here!
		body.addElement("64","0");
		sendPacket(body,SERVICE_MESSAGE,STATUS_OFFLINE);	// 0x06
		// -----If we have a typing notifier, inform it the typing has ended
		TypingNotifier tn = (TypingNotifier)typingNotifiers.get(to);
		if(tn!=null)  tn.stopTyping();
	}

	// -----------------------------------------------------------------
	// Transmit a NOTIFY packet.  Could be used for all sorts of purposes,
	// but mainly games and typing notifications.  Only typing is supported
	// by this API.  The mode determines the type of notification, "TYPING"
	// or "GAME"; msg holds the game name (or a single space if typing).
	// -----------------------------------------------------------------
	protected void transmitNotify(String friend,String yid,boolean on,String msg,String mode) throws IOException
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		body.addElement("4",yid);
		body.addElement("5",friend);
		body.addElement("14",msg);
		if(on)  body.addElement("13","1");
			else  body.addElement("13","0");
		body.addElement("49",mode);
		sendPacket(body,SERVICE_NOTIFY,STATUS_TYPING);		// 0x4b
	}

	// -----------------------------------------------------------------
	// Transmit a PING packet.
	// -----------------------------------------------------------------
	protected void transmitPing() throws IOException
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		sendPacket(body,SERVICE_PING);						// 0x12
	}

	// -----------------------------------------------------------------
	// Transmit a USERSTAT packet.  Purpose?  Unknown  :-)
	// It would seem that transmiting this packet results in a USERSTAT
	// (0x0a) packet followed by a NEWMAIL (0x0b) packet with just the
	// 9 field (new mail count) set being sent by the server.
	// -----------------------------------------------------------------
	protected void transmitUserStat() throws IOException	// 0x0a
	{	PacketBodyBuffer body = new PacketBodyBuffer();
		sendPacket(body,SERVICE_USERSTAT);
	}

	// -----------------------------------------------------------------
	// Process an incoming ADDIGNORE packet.  We get one of these when we
	// ignore/unignore someone, although their purpose is unknown as
	// Yahoo follows up with a CONTACTIGNORE packet.  The only disting-
	// uising feature is the latter is always sent, wereas this packet
	// is only sent if there's an actual change in ignore status.  The
	// packet's payload appears to always be empty.
	// -----------------------------------------------------------------
	protected void receiveAddIgnore(YMSG9Packet pkt)		// 0x11
	{	// Not implementation (yet!)
	}

	// -----------------------------------------------------------------
	// Process an incoming AUTH packet (in response to the AUTH packet
	// we transmitted to the server).
	// Format: "1" <loginID> "94" <challenge string (24 chars)>
	// Note: for YMSG10 Yahoo sneakily changed the challenge/response
	// method dependant upon a switch in field '13'.  If this field
	// is 0 use v9, if 1 then use v10.
	// -----------------------------------------------------------------
	protected void receiveAuth(YMSG9Packet pkt) 			// 0x57
	throws IOException,NoSuchAlgorithmException
	{	String v10 = pkt.getValue("13");					// '0'=v9, '1'=v10
		String[] s;
		try
		{	if(v10!=null && v10.equals("1"))
				s=ChallengeResponseV10.getStrings(loginID,password,pkt.getValue("94"));
			else
				s=ChallengeResponseV9.getStrings(loginID,password,pkt.getValue("94"));
		}
		catch(NoSuchAlgorithmException e) { throw e; }
		catch(Exception e) { throw new YMSG9BadFormatException("auth",false,e); }
		transmitAuthResp(s[0],s[1]);
	}

	// -----------------------------------------------------------------
	// Process an incoming AUTHRESP packet.  If we get one of these it
	// means the logon process has failed.  Set the session state to be
	// failed, and flag the end of login.
	// Note: we don't throw exceptions on the input thread, but instead
	// we pass them to the thread which called login()
	// -----------------------------------------------------------------
	protected void receiveAuthResp(YMSG9Packet pkt)			// 0x54
	{	try
		{	if(pkt.exists("66"))
			{	long l = Long.parseLong(pkt.getValue("66"));
				if(l==STATUS_LOCKED)	// Account locked out?
				{	URL u;
					try { u = new URL(pkt.getValue("20")); } catch(Exception e) { u=null; }
					loginException = new AccountLockedException("User "+loginID+" has been locked out",u);
				}
				else if(l==STATUS_BAD)	// Bad login (password?)
				{	loginException = new LoginRefusedException("User "+loginID+" refused login",l);
				}
				else if(l==STATUS_BADUSERNAME) // Unknown account?
				{	loginException = new LoginRefusedException("User "+loginID+" unknown",l);
				}
			}
		}catch(NumberFormatException e) {}
		// -----Ensure the ipThread loop which called this method now exits
		ipThread.quit=true;
		// -----Notify login() calling thread of failure
		sessionStatus=FAILED;  loginOver=true;
	}

	// -----------------------------------------------------------------
	// Process an incoming CHATCONNECT packet.  We get one of these in
	// reply to sending a CHATCONNECT packet to Yahoo.  It marks the end
	// of the first stage of the chat login handshake.  chatLogin() waits
	// for this packet before proceeding to the next stage.
	// -----------------------------------------------------------------
	protected void receiveChatConnect(YMSG9Packet pkt)		// 0x96
	{	chatConnectOver=true;
	}

	// -----------------------------------------------------------------
	// Process an incoming CHATDISCONNECT packet.  We get one of these to
	// confirm we have logged off.  (Note: when sending a CHATDISCONNECT
	// packet we should get in reply a CHATLOGOFF prior to this packet!)
	// -----------------------------------------------------------------
	protected void receiveChatDisconnect(YMSG9Packet pkt)	// 0xa0
	{	// -----This should already have been set by transmitChatDisconnect(),
		// -----if it isn't set then we have been booted out without actually
		// -----asking to leave.  This usually happens when Yahoo times us out
		// -----of a room after a long period of inactivity.
		if(chatSessionStatus!=UNSTARTED)
		{	new FireEvent().fire(new SessionEvent(this),SERVICE_CHATDISCONNECT);
		}
		chatSessionStatus=UNSTARTED;
	}

	// -----------------------------------------------------------------
	// Process an incoming CHATLOGOFF packet.  We get one of these when
	// someone leaves the chatroom - including ourselves, received just
	// prior to our CHATDISCONNECT confirmation packet (see above).
	// Note: on rare occassions this packet has been received for a user
	// we previously had not been informed about.
	// -----------------------------------------------------------------
	protected void receiveChatLogoff(YMSG9Packet pkt)		// 0x9b
	{	try
		{	String netname = pkt.getValue("104");			// room:lobby
			String id = pkt.getValue("109");				// Yahoo id
			YahooChatLobby ycl = YahooChatCategory.getLobby(netname);
			if(ycl==null)  throw new NoSuchChatroomException("Chatroom/lobby "+netname+" not found.");
			// -----Remove user from room (very very occassionally the user does
			// -----not exist as a known member of this lobby, so ycu==null!!)
			YahooChatUser ycu = ycl.getUser(id);
			if(ycu!=null)  ycl.removeUser(ycu);
				else  ycu=createChatUser(pkt,0);	// Create for benefit of event!
			// -----Create and fire event   FIX: should cope with multiple users!
			SessionChatEvent se = new SessionChatEvent(this,1,ycl);
			se.setChatUser(0,ycu);
			new FireEvent().fire(se,SERVICE_CHATLOGOFF);
		}catch(Exception e) { throw new YMSG9BadFormatException("chat logoff",false,e); }
	}

	// -----------------------------------------------------------------
	// Process an incoming CHATLOGON packet.  We get one of these:
	// (a) as a way of finishing the login handshaking process, containing
	//     room details (we already know) and a list of current members.
	// (b) when the login process fails (room full?), containing only a 114
	//     field (set to '-35'?) - see error handling code elsewhere
	// (c) as a stripped down version when a new user enters the room
	//     (including ourselves!)
	// (d) as a stripped down version when an existing member updates
	//     their details.
	// Sometimes a login packet is broken over several packets.  The clue
	// here appears to be that the first (and subsequent?) packets have a
	// status of 5, while the final packet has a status of 1.
	// -----------------------------------------------------------------
	protected void receiveChatLogon(YMSG9Packet pkt)		// 0x98
	{	boolean joining=false;
		try
		{	// -----Is this an error packet, sent to us via processError() ?
			if(pkt.exists("114"))
			{	loginException = new LoginRefusedException("User "+chatID+" refused chat login");
				joining=true;  chatSessionStatus=FAILED;
				return;  // ...to finally block
			}

			// -----Returns null if more packets to come
			pkt=compoundChatLoginPacket(pkt);
			if(pkt==null)  return;

			// -----As we need to load a room to get at its lobby data so we
			// -----can login, the next line *should* never fail... however :-)
			String netname = pkt.getValue("104");			// room:lobby
			YahooChatLobby ycl = YahooChatCategory.getLobby(netname);
			if(ycl==null)  throw new NoSuchChatroomException("Chatroom/lobby "+netname+" not found.");

			// -----Note: Yahoo sometimes lies about the '108' count of users!
			// -----Reduce count until to see how many users there *actually* are!
			int cnt=Integer.parseInt( pkt.getValue("108") );
			while(cnt>0 && pkt.getNthValue("109",cnt-1)==null)  cnt--;

			// -----Is this an update packet, for an existing member?
			YahooChatUser ycu=ycl.getUser(pkt.getValue("109"));
			if(cnt==1 && ycu!=null)
			{	// -----Count is one and user exists - UPDATE
				ycu.update
				(	pkt.getValue("113") ,				// Attributes
					pkt.getValue("141") ,				// Alias (optional)
					pkt.getValue("110") ,				// Age (or zero)
					pkt.getValue("142")					// Location (optional)
				);
				SessionChatEvent se = new SessionChatEvent(this,1,ycl);
				se.setChatUser(0,ycu);
				new FireEvent().fire(se,SERVICE_X_CHATUPDATE);
				return;  // ...to finally block
			}
			else
			{	// -----Count is gt one, or user unknown - ADD

				// -----Full sized packet, when joining room?
				joining = pkt.exists("61");
				// -----If we are joining, clear the array of users (just to be sure!)
				if(joining)  ycl.clearUsers();

				// -----When sent in muliple parts the login packet usually 
				// -----contains a high degree of duplicates.  Remove using hash.
				Hashtable ht = new Hashtable();
				for(int i=0;i<cnt;i++)
				{	// -----Note: automatically creates YahooUser if necessary
					ycu = createChatUser(pkt,i);
					ht.put(ycu.getId() , ycu);
				}
				// -----Create event, add users
				SessionChatEvent se = new SessionChatEvent(this,cnt,ycl);
				int i=0;
				for(Enumeration en=ht.elements();en.hasMoreElements();)
				{	ycu = (YahooChatUser)en.nextElement();
					// -----Does this user exist already?  (This should always be
					// -----no, as update packets should always have only one member
					// -----who already exists - thus caught by the 'if' block above!
					//System.out.println(">>>>>>>>>>>"+ycu.toString());
					if(!ycl.exists(ycu))  ycl.addUser(ycu);	// Add to lobby
					se.setChatUser(i++,ycu);				// Add to event
				}

				// -----We don't send an event if we get the larger 'logging in'
				// -----type packet as the chat user list is brand new.  We only send
				// -----events when someone joins and we need to signal an update.
				if(!joining)
				{	// -----Did we actually accrue any *new* users in previous loop?
					if(se.getChatUsers().length>0)
						new FireEvent().fire(se,SERVICE_CHATLOGON);
				}
				else
				{	chatSessionStatus=MESSAGING;
				}
				return;  // ...to finally block
			}
		}
		catch(Exception e)
		{	e.printStackTrace();
			throw new YMSG9BadFormatException("chat login",false,e);
		}
		finally
		{	// FIX: Not thread safe if multiple chatroom supported!
			if(joining)  chatLoginOver=true;
		}
	}

	// -----------------------------------------------------------------
	// Process an incoming CHATMSG packet.  We get one of these when
	// a chatroom user sends a message to the room.  (Note: very very
	// occassionally we *might* get a message from a user we have not
	// been told about yet!  I've not seen any chat message packets like
	// this, but it *does* happen with other chat packets, so I have to
	// assume it can happen to chat message packets too!)
	// Note: status checked because we may have timed out on chat login
	// -----------------------------------------------------------------
	protected void receiveChatMsg(YMSG9Packet pkt)			// 0xa8
	{	if(chatSessionStatus!=MESSAGING)  return;
		try
		{	String netname = pkt.getValue("104");			// room:lobby
			YahooChatLobby ycl = YahooChatCategory.getLobby(netname);
			if(ycl==null)  throw new NoSuchChatroomException("Chatroom/lobby "+netname+" not found.");
			// -----Create and fire event
			YahooChatUser ycu = ycl.getUser(pkt.getValue("109"));
			if(ycu==null)  ycu=createChatUser(pkt,0);
			SessionChatEvent se = new SessionChatEvent
			(	this,
				ycu,										// from
				pkt.getValue("117"),						// message
				pkt.getValue("124"),						// 1=Regular, 2=Emote
				ycl											// room:lobby
			);
			new FireEvent().fire(se,SERVICE_CHATMSG);
		}catch(Exception e) { throw new YMSG9BadFormatException("chat message",false,e); }
	}

	// -----------------------------------------------------------------
	// Process an incoming CHATPM packet.  We get one of these when
	// someone 'personal messages' us from within a chatroom.  Why this
	// packet is used, not a regular MESSAGE packet is unknown. It seems
	// that the web-based chat clients, at least, prefer this packet type.
	// Note: status checked because we may have timed out on chat login
	// -----------------------------------------------------------------
	protected void receiveChatPM(YMSG9Packet pkt)			// 0x20
	{	if(chatSessionStatus!=MESSAGING)  return;
		try
		{	SessionEvent se = new SessionEvent
			(	this,
				pkt.getValue("5"),						// to
				pkt.getValue("4"),						// from
				pkt.getValue("14")						// message
			);
			new FireEvent().fire(se,SERVICE_MESSAGE);
		}catch(Exception e) { throw new YMSG9BadFormatException("chat PM",false,e); }
	}

	// -----------------------------------------------------------------
	// Process an incoming CONFINVITE packet.  We get one of these when
	// we are being invited to join someone else's existing conference.
	// To all intent and purpose this (I assume) is the same as a
	// regular invite packet, except it is only delievered to one source,
	// not everyone on the list (?)
	// -----------------------------------------------------------------
	protected void receiveConfAddInvite(YMSG9Packet pkt)	// 0x01c
	{	receiveConfInvite(pkt);
	}
	
	// -----------------------------------------------------------------
	// Process an incoming CONFDECLINE packet.  We get one of these when
	// someone we previously invited to a conference, declines our invite.
	// -----------------------------------------------------------------
	protected void receiveConfDecline(YMSG9Packet pkt)		// 0x1a
	{	try
		{	YahooConference yc = getOrCreateConference(pkt);
			// -----Create event
			SessionConferenceEvent se = new SessionConferenceEvent
			(	this,
				pkt.getValue("1"),							// to (effective id)
				pkt.getValue("54"),							// from
				pkt.getValue("14"),							// message (topic)
				yc,											// room
				null										// users array
			);
			// -----Remove the user
			yc.removeUser(se.getFrom());
			// -----Fire invite event
			if(!yc.isClosed())  // Should never be closed!
				new FireEvent().fire(se,SERVICE_CONFDECLINE);
		}catch(Exception e) { throw new YMSG9BadFormatException("conference decline",false,e); }
	}

	// -----------------------------------------------------------------
	// Process an incoming CONFINVITE packet.  We get one of these when
	// we are being invited to join someone else's conference.  Note:
	// it is possible for conference packets (ie: logon) can arrive before
	// the invite.  These are buffered until the invite is received.
	// -----------------------------------------------------------------
	protected void receiveConfInvite(YMSG9Packet pkt)		// 0x18
	{	try
		{	YahooConference yc = getOrCreateConference(pkt);
			String[] users = pkt.getValues("52");
			// -----Create event
			SessionConferenceEvent se = new SessionConferenceEvent
			(	this,
				pkt.getValue("1"),							// to (effective id)
				pkt.getValue("50"),							// from
				pkt.getValue("58"),							// message (topic)
				yc,											// room
				userStore.toUserArray(users)				// users array
			);
			// -----Add the users
			yc.addUsers(users);  yc.addUser(se.getFrom());
			// -----Fire invite event
			if(!yc.isClosed())  // Should never be closed for invite!
				new FireEvent().fire(se,SERVICE_CONFINVITE);
			// -----Set invited status and work through buffered conference
			synchronized(yc)
			{	Vector v = yc.inviteReceived();
				for(int i=0;i<v.size();i++)
					ipThread.process((YMSG9Packet)v.elementAt(i));
			}
		}catch(Exception e) { throw new YMSG9BadFormatException("conference invite",false,e); }
	}

	// -----------------------------------------------------------------
	// Process an incoming CONFLOGOFF packet.  We get one of these when
	// someone leaves the conference.  Note: in *very* extreme circum-
	// stances, this may arrive before the invite packet.
	// -----------------------------------------------------------------
	protected void receiveConfLogoff(YMSG9Packet pkt)		// 0x1b
	{	// -----If we have not received an invite yet, buffer packets
		YahooConference yc = getOrCreateConference(pkt);
		synchronized(yc)
		{	if(!yc.isInvited()) { yc.addPacket(pkt);  return; }
		}
		// -----Otherwise, handle the packet
		try
		{	SessionConferenceEvent se = new SessionConferenceEvent
			(	this,
				pkt.getValue("1"),							// to (effective id)
				pkt.getValue("56"),							// from
				null,										// message
				yc											// room
			);
			// -----Remove the user
			yc.removeUser(se.getFrom());
			// -----Fire invite event
			if(!yc.isClosed())  // Should never be closed fir invite!
				new FireEvent().fire(se,SERVICE_CONFLOGOFF);
		}catch(Exception e) { throw new YMSG9BadFormatException("conference logoff",false,e); }
	}

	// -----------------------------------------------------------------
	// Process an incoming CONFLOGON packet.  We get one of these when
	// someone joins a conference we have been invited to (even if we
	// ourselves have yet to accept/decline).  Note: in extreme circum-
	// stances, this may arrive before the invite packet.
	// -----------------------------------------------------------------
	protected void receiveConfLogon(YMSG9Packet pkt)		// 0x19
	{	// -----If we have not received an invite yet, buffer packets
		YahooConference yc = getOrCreateConference(pkt);
		synchronized(yc)
		{	if(!yc.isInvited()) { yc.addPacket(pkt);  return; }
		}
		// -----Otherwise, handle the packet
		try
		{	SessionConferenceEvent se = new SessionConferenceEvent
			(	this,
				pkt.getValue("1"),							// to (effective id)
				pkt.getValue("53"),							// from (accepting user)
				null,										// message
				yc											// room
			);
			// -----Add user (probably already on list, but just to be sure!)
			yc.addUser(se.getFrom());
			// -----Fire event
			if(!yc.isClosed())
				new FireEvent().fire(se,SERVICE_CONFLOGON);
		}catch(Exception e) { System.out.println("looy");throw new YMSG9BadFormatException("conference logon",false,e); }
	}

	// -----------------------------------------------------------------
	// Process an incoming CONFMSG packet.  We get one of these when
	// someone in a conference we are part of sends a message.  Note:
	// in extreme circumstances this may arrive before the invite packet.
	// -----------------------------------------------------------------
	protected void receiveConfMsg(YMSG9Packet pkt)			// 0x1d
	{	// -----If we have not received an invite yet, buffer packets
		YahooConference yc = getOrCreateConference(pkt);
		synchronized(yc)
		{	if(!yc.isInvited()) { yc.addPacket(pkt);  return; }
		}
		// -----Otherwise, handle the packet
		try
		{	SessionConferenceEvent se = new SessionConferenceEvent
			(	this,
				pkt.getValue("1"),							// to (effective id)
				pkt.getValue("3"),							// from
				pkt.getValue("14"),							// message
				yc											// room
			);
			// -----Fire event
			if(!yc.isClosed())
				new FireEvent().fire(se,SERVICE_CONFMSG);
		}catch(Exception e) { throw new YMSG9BadFormatException("conference mesg",false,e); }
	}

	// -----------------------------------------------------------------
	// Process an incoming CONTACTIGNORE packet. We get one of these to
	// confirm an outgoing CONTACTIGNORE - an ADDIGNORE packet may preceed
	// this, but only if the ignore status has genuinely changed state.
	// -----------------------------------------------------------------
	protected void receiveContactIgnore(YMSG9Packet pkt)	// 0x85
	{	try
		{	String n = pkt.getValue("0");
			boolean ig = pkt.getValue("13").charAt(0)=='1';
			int st = Integer.parseInt(pkt.getValue("66"));
			if(st==0)
			{	// -----Update ignore status, and fire friend changed event
				YahooUser yu = userStore.getOrCreate(n);
				yu.setIgnored(ig);
				// -----Fire event
				SessionFriendEvent se = new SessionFriendEvent(this,1);
				se.setUser(0,yu);
				new FireEvent().fire(se,SERVICE_ISAWAY);
			}
			else
			{	// -----Error
				String m="Contact ignore error: ";
				switch(st)
				{	case 2 :	m=m+"Already on ignore list";  break;
					case 3 :	m=m+"Not currently ignored";  break;
					case 12 :	m=m+"Cannot ignore friend";  break;
					default :	m=m+"Unknown error";  break;
				}
				errorMessage(pkt,m);
			}
		}catch(Exception e) { throw new YMSG9BadFormatException("contact ignore",false,e); }
	}

	// -----------------------------------------------------------------
	// Process an incoming CONTACTNEW packet.  We get one of these: (1) when
	// someone has added us to their friends list, giving us the chance to
	// refuse them;  (2) when we add or remove a friend (see FRIENDADD/REMOVE
	// outgoing) as confirmation prior to the FRIENDADD/REMOVE packet being
	// echoed back to us - if the friend is online status info may be inc-
	// luded (supposedly for multiple friends, although given the circum-
	// stances probably always contains only one!);  (3) when someone refuses
	// a contact request (add friend) from us.
	// -----------------------------------------------------------------
	protected void receiveContactNew(YMSG9Packet pkt)		// 0x0f
	{	try
		{	if(pkt.length<=0)					// Empty packet is received after
			{	return;							// we transmit FRIENDADD/REMOVE
			}
			else if(pkt.exists("7"))			// Ditto, except friend is online
			{	updateFriendsStatus(pkt);  return;
			}
			else if(pkt.status==0x07)			// Conact refused
			{	SessionEvent se = new SessionEvent
				(	this,
					null,									// to
					pkt.getValue("3"),						// from
					pkt.getValue("14")						// message
				);
				new FireEvent().fire(se,SERVICE_CONTACTREJECT);
			}
			else								// Contact request
			{	SessionEvent se = new SessionEvent
				(	this,
					pkt.getValue("1"),						// to
					pkt.getValue("3"),						// from
					pkt.getValue("14"),						// message
					pkt.getValue("15")  					// timestamp
				);
				se.setStatus(pkt.status);  // status!=0 means offline message
				new FireEvent().fire(se,SERVICE_CONTACTNEW);
			}
		}catch(Exception e) { throw new YMSG9BadFormatException("contact request",false,e); }
	}

	// -----------------------------------------------------------------
	// Process an incoming FILETRANSFER packet.  This packet can be
	// received under two circumstances: after a successful FT upload,
	// in which case it contains a text message with the download URL,
	// or because someone has sent us a file.  Note: TF packets do not
	// contain the file data itself, but rather a link to a tmp area on
	// Yahoo's webservers which holds the file.
	// -----------------------------------------------------------------
	protected void receiveFileTransfer(YMSG9Packet pkt)		// 0x46
	{	try
		{	if(!pkt.exists("38"))				// Acknowledge upload
			{	SessionEvent se = new SessionEvent
				(	this,
					pkt.getValue("5"),						// to
					pkt.getValue("4"),						// from
					pkt.getValue("14")						// message
				);
				new FireEvent().fire(se,SERVICE_MESSAGE);
			}
			else								// Receive file transfer
			{	SessionFileTransferEvent se = new SessionFileTransferEvent
				(	this,
					pkt.getValue("5"),						// to
					pkt.getValue("4"),						// from
					pkt.getValue("14"),						// message
					pkt.getValue("38"), 					// expires
					pkt.getValue("20")						// URL
				);
				new FireEvent().fire(se,SERVICE_FILETRANSFER);
			}
		}catch(Exception e) { throw new YMSG9BadFormatException("file transfer",false,e); }

	}

	// -----------------------------------------------------------------
	// Process an incoming FRIENDADD packet.  We get one of these after
	// we've sent a FRIENDADD packet, as confirmation.  It contains
	// basic details of our new friend, although it seems a bit redundant
	// as Yahoo sents a CONTACTNEW with these details before this packet.
	// -----------------------------------------------------------------
	protected void receiveFriendAdd(YMSG9Packet pkt)		// 0x83
	{	try
		{	// -----We probably got a CONTACTNEW before we got this packet, so
			// -----check if the user hasn't already been created in users hash
			// -----(if not, create!) then add to groups structure.
			String n=pkt.getValue("7") , s=pkt.getValue("66") , g=pkt.getValue("65");
			YahooUser yu = userStore.getOrCreate(n);
			insertFriend(yu,g);
			// -----Fire event : 7=friend, 66=status, 65=group name
			SessionFriendEvent se = new SessionFriendEvent(this,yu,g);
			new FireEvent().fire(se,SERVICE_FRIENDADD);
		}catch(Exception e) { throw new YMSG9BadFormatException("friend added",false,e); }
	}

	// -----------------------------------------------------------------
	// Process an incoming FRIENDADD packet.  We get one of these after
	// we've sent a FRIENDREMOVE packet, as confirmation.  It contains
	// basic details of the friend we've deleted.
	// -----------------------------------------------------------------
	protected void receiveFriendRemove(YMSG9Packet pkt)		// 0x84
	{	try
		{	String n=pkt.getValue("7") , g=pkt.getValue("65");
			YahooUser yu = userStore.get(n);
			if(yu==null) { report("Unknown friend",pkt);  return; }
			deleteFriend(yu,g);
			// -----Fire event : 7=friend, 66=status, 65=group name
			SessionFriendEvent se = new SessionFriendEvent(this,yu,g);
			new FireEvent().fire(se,SERVICE_FRIENDREMOVE);
		}catch(Exception e) { throw new YMSG9BadFormatException("friend removed",false,e); }
	}

	// -----------------------------------------------------------------
	// Process an incoming IDACT packet.
	// -----------------------------------------------------------------
	protected void receiveIdAct(YMSG9Packet pkt)			// 0x07
	{	// FIX: do something here!
	}
	
	// -----------------------------------------------------------------
	// Process an incoming IDDEACT packet.
	// -----------------------------------------------------------------
	protected void receiveIdDeact(YMSG9Packet pkt)			// 0x08
	{	// Fix: do something here!
	}

	// -----------------------------------------------------------------
	// Process an incoming ISAWAY packet.  See ISBACK below.
	// -----------------------------------------------------------------
	protected void receiveIsAway(YMSG9Packet pkt)			// 0x03
	{	// -----If this an update to a friend?
		if(pkt.exists("7"))
		{	updateFriendsStatus(pkt);
		}
	}

	// -----------------------------------------------------------------
	// Process an incoming ISBACK packet.  God alone knows what I'm supposed
	// to do with this when the payload is empty!!
	// -----------------------------------------------------------------
	protected void receiveIsBack(YMSG9Packet pkt)			// 0x04
	{	if(pkt.exists("7"))
		{	updateFriendsStatus(pkt);
		}
	}

	// -----------------------------------------------------------------
	// Process and incoming LIST packet.  We'll typically get one of these
	// when our logon is sucessful.  (It should arrive before the LOGON
	// packet.)  Note: this packet can arrive in several parts.  Taking a
	// look at other third-party YMSG implemenations it would seem that
	// the absence of cookies is the trait marking an incomplete packet.
	// -----------------------------------------------------------------
	protected void receiveList(YMSG9Packet pkt)				// 0x55
	{	// -----These fields will be concatenated, others will be appended
		String[] concatFields = { "87","88","89" };
		// -----Either cache or merge with cached packet
		if(cachePacket==null)  cachePacket=pkt;
			else  cachePacket.merge(pkt,concatFields);
		// -----Complete: this is the final packet
		if(pkt.exists("59"))  _receiveList(cachePacket);
	}	
	private void _receiveList(YMSG9Packet pkt)			// 0x55
	{	// -----Friends list, each group is encoded as the group name
		// -----(ie: "Friends") followed by a colon, followed by a comma
		// -----separated list of friend ids, followed by a single \n (0x0a).
		try
		{	//System.out.println("--------\n"+pkt.toString()+"\n--------");
			String s = pkt.getValue("87");		// Value for key "87"
			if(s!=null)
			{	StringTokenizer st1 = new StringTokenizer(s,"\n");
				groups = new YahooGroup[st1.countTokens()];
				int i=0;
				while(st1.hasMoreTokens())
				{	// -----Extract group
					String s1 = st1.nextToken();
					// -----Store group name and decoded friends list
					groups[i] = new YahooGroup( s1.substring(0,s1.indexOf(":")) );
					StringTokenizer st2 = new StringTokenizer( s1.substring(s1.indexOf(":")+1),"," );
					while(st2.hasMoreTokens())
					{	YahooUser yu;
						String k = st2.nextToken();
						// -----Same user can appear in more than one group
						yu=userStore.getOrCreate(k);
						// -----Add to group
						groups[i].addUser(yu);  yu.adjustGroupCount(+1);
					}
					i++;
				}
			}
		}catch(Exception e) { throw new YMSG9BadFormatException("friends list in list",false,e); }

		// -----Ignored list (people we don't want to hear from!)
		try
		{	String s = pkt.getValue("88");		// Value for key "88"
			if(s!=null)
			{	// -----Comma separated list (?)
				StringTokenizer st = new StringTokenizer(s,",");
				while(st.hasMoreTokens())
				{	s=st.nextToken();
					YahooUser yu = userStore.getOrCreate(s);
					yu.setIgnored(true);
				}
			}
		}catch(Exception e) { throw new YMSG9BadFormatException("ignored list in list",false,e); }

		// -----Identities list (alternative yahoo ids we can use!)
		try
		{	String s = pkt.getValue("89");		// Value for key "89"
			if(s!=null)
			{	// -----Comma separated list (?)
				StringTokenizer st = new StringTokenizer(s,",");
				int i=0;
				identities = new YahooIdentity[st.countTokens()];
				while(st.hasMoreTokens())
				{	identities[i++] = new YahooIdentity(st.nextToken());
				}
			}
		}catch(Exception e) { throw new YMSG9BadFormatException("identities list in list",false,e); }

		// -----Yahoo gives us three cookies, Y, T and C
		try
		{	String[] ck = ConnectionHandler.extractCookies(pkt);
			cookieY=ck[COOKIE_Y]; 	// Y=<cookie>
			cookieT=ck[COOKIE_T];	// T=<cookie>
			cookieC=ck[COOKIE_C];	// C=<cookie>
		}catch(Exception e) { throw new YMSG9BadFormatException("cookies in list",false,e); }

		// -----Primary identity: the *real* Yahoo ID for this account.
		// -----Only present if logging in under non-primary identity(?)
		try
		{	if(pkt.exists("3"))  primaryID = pkt.getValue("3").trim();
				else  primaryID = loginID;
		}catch(Exception e) { throw new YMSG9BadFormatException("primary identity in list",false,e); }

		// -----Set the primary and login flags on the relevant YahooIdentity objects
		identityIdToObject(primaryID).setPrimaryIdentity(true);
		identityIdToObject(loginID).setLoginIdentity(true);
		
		// -----If this was sent outside the login process is over, send an event
		if(loginOver)  new FireEvent().fire(new SessionEvent(this),SERVICE_LIST);
	}

	// -----------------------------------------------------------------
	// Process an incoming LOGOFF packet.  If we get one of these it means
	// Yahoo wants to throw us off the system.  When logging in using the
	// same Yahoo ID using a second client, I noticed the Yahoo server sent
	// one of these (just a header, no contents) and closed the socket.
	// -----------------------------------------------------------------
	protected void receiveLogoff(YMSG9Packet pkt)			// 0x02
	{	// -----Is this packet about us, or one of our online friends?
		if(!pkt.exists("7"))					// About us
		{	// -----Note: when this method returns, the input thread loop
			// -----which called it exits.
			sessionStatus=UNSTARTED;  ipThread.quit=true;
		}
		else									// About friends
		{	// -----Process optional section, friends going offline
			try
			{	updateFriendsStatus(pkt);
			}catch(Exception e) { throw new YMSG9BadFormatException("online friends in logoff",false,e); }
		}
	}

	// -----------------------------------------------------------------
	// Process an incoming LOGON packet.  If we get one of these it means
	// the logon process has been successful.  If the user has friends
	// already online, an extra section of varying length is appended,
	// starting with a count, and then detailing each friend in turn.
	// -----------------------------------------------------------------
	protected void receiveLogon(YMSG9Packet pkt)			// 0x01
	{	// -----Is this packet about us, or one of our online friends?
		if(pkt.exists("7"))
		{	// -----Process optional section, friends currently online
			try
			{	updateFriendsStatus(pkt);
			}catch(Exception e) {
				//System.out.println("onlisadfasdf");
				throw new YMSG9BadFormatException("Looy online friends in logon",false,e); 
			}
		}
		// -----Still logging in?
		if(!loginOver)
		{	try
			{	if(status==STATUS_AVAILABLE)  transmitIsBack();
					else  transmitIsAway();
			}catch(IOException e) {}
			sessionStatus=MESSAGING;  loginOver=true;
		}
	}

	// -----------------------------------------------------------------
	// Process an incoming MESSAGE packet.  Message can be either online
	// or offline, the latter having a datestamp of when they were sent.
	// -----------------------------------------------------------------
	protected void receiveMessage(YMSG9Packet pkt)			// 0x06
	{	try
		{	if(!pkt.exists("14"))					// Contains no message?
			{	return;
			}
			else if(pkt.status==STATUS_NOTINOFFICE)	// Sent while we were offline
			{	int i=0;
				// -----Read each message, until null
				String s = pkt.getNthValue("31",i);
				while(s!=null)
				{	SessionEvent se = new SessionEvent
					(	this,
						pkt.getNthValue("5",i),				// to
						pkt.getNthValue("4",i),				// from
						pkt.getNthValue("14",i),			// message
						pkt.getNthValue("15",i)				// timestamp
					);
					new FireEvent().fire(se,SERVICE_X_OFFLINE);
					i++;  s=pkt.getNthValue("31",i);
				}
			}
			else								// Sent while we are online
			{	SessionEvent se = new SessionEvent
				(	this,
					pkt.getValue("5"),						// to
					pkt.getValue("4"),						// from
					pkt.getValue("14")						// message
				);
			  //  System.out.println("looymsg="+pkt.getValue("14"));
				if(se.getMessage().equalsIgnoreCase(BUZZ))
					new FireEvent().fire(se,SERVICE_X_BUZZ);
				else
					new FireEvent().fire(se,SERVICE_MESSAGE);
			}
		}catch(Exception e) { throw new YMSG9BadFormatException("message",false,e); }
	}

	// -----------------------------------------------------------------
	// Process an incoming NEWMAIL packet, informing us of how many unread
	// Yahoo mail messages we have.
	// -----------------------------------------------------------------
	protected void receiveNewMail(YMSG9Packet pkt)			// 0x0b
	{	try
		{	SessionNewMailEvent se;
			if(!pkt.exists("43"))				// Count only
			{	se = new SessionNewMailEvent
				(	this,
					pkt.getValue("9")						// new mail count
				);
			}
			else								// Mail message
			{	se = new SessionNewMailEvent
				(	this,
					pkt.getValue("43"),						// from
					pkt.getValue("42"),						// email address
					pkt.getValue("18")						// subject
				);
			}
			new FireEvent().fire(se,SERVICE_NEWMAIL);
		}catch(Exception e) { throw new YMSG9BadFormatException("new mail",false,e); }
	}

	// -----------------------------------------------------------------
	// Process an incoming NOTIFY packet.  "Typing" for example.  (Why
	// these things needs to be sent is beyond me!)
	// -----------------------------------------------------------------
	protected void receiveNotify(YMSG9Packet pkt)			// 0x4b
	{	try
		{	if(pkt.status == 0x01) 	// FIX: documentation says this should be STATUS_TYPING (0x16)
			{	SessionNotifyEvent se = new SessionNotifyEvent
				(	this,
					pkt.getValue("5"),						// to
					pkt.getValue("4"),						// from
					pkt.getValue("14"),						// message (game)
					pkt.getValue("49"),						// type (typing/game)
					pkt.getValue("13")						// mode (on/off)
				);
				se.setStatus(pkt.status);
				new FireEvent().fire(se,SERVICE_NOTIFY);
			}
		}catch(Exception e) { throw new YMSG9BadFormatException("notify",false,e); }
	}

	// -----------------------------------------------------------------
	// Process and incoming PING packet.  When logging in under v10, this
	// packet is sent appended to a LOGON.  It contains only two fields,
	// 143 and 144.  Purpose as yet unknown.
	// -----------------------------------------------------------------
	protected void receivePing(YMSG9Packet pkt)				// 0x12
	{}

	// -----------------------------------------------------------------
	// Process and incoming USERSTAT packet.
	// -----------------------------------------------------------------
	protected void receiveUserStat(YMSG9Packet pkt)			// 0x0a
	{	status = pkt.status;
	}

	// -----------------------------------------------------------------
	// Process an error CHATLOGIN packet.  The only time these seem to be
	// sent is when we fail to connect to a chat room - perhaps because it
	// is full (?)
	// -----------------------------------------------------------------
	protected void erroneousChatLogin(YMSG9Packet pkt)		// 0x98
	{	chatSessionStatus=FAILED;  chatLoginOver=true;
	}

	// -----------------------------------------------------------------
	// Note: the term 'packet' here refers to a YMSG message, not a TCP packet
	// (although in almost all cases the two will be synonymous).  This is to
	// avoid confusion with a 'YMSG message' - the actual discussion packet.
	//
	// service - the Yahoo service number
	// status - the Yahoo status number (not sessionStatus above!)
	// body - the payload of the packet
	//
	// Note: it is assumed that the ConnectionHandler has been open()'d
	// -----------------------------------------------------------------
	protected void sendPacket(PacketBodyBuffer body,int service,long status) throws IOException
	{	network.sendPacket(body,service,status,sessionId);
	}

	protected void sendPacket(PacketBodyBuffer body, int service) throws IOException
	{	sendPacket(body,service,STATUS_AVAILABLE);
	}

	// -----------------------------------------------------------------
	// Dump a report out to stdout
	// -----------------------------------------------------------------
	private void report(String s,YMSG9Packet p)
	{	System.err.println(s+"\n"+p.toString()+"\n");
	}

	// -----------------------------------------------------------------
	// Convenience method - have we passed a given time?  (More readable)
	// -----------------------------------------------------------------
	private boolean past(long time)
	{	return (System.currentTimeMillis()>time);
	}

	// -----------------------------------------------------------------
	// Start threads
	// -----------------------------------------------------------------
	private void startThreads()
	{	ipThread = new InputThread();
		pingThread = new PingThread();
	}

	// -----------------------------------------------------------------
	// The chances are our input thread will be blocked, and so will not
	// check the quit flag.  If I force the socket closed, this will
	// apparently unblock the IO (well... you'd expect it to!) and cause
	// the thread to die gracefully without a ThreadDeath.
	// -----------------------------------------------------------------
	/*private void killInputThread()
	{	if(ipThread!=null)
		{	ipThread.quit=true;  ipThread.interrupt();  ipThread=null;
			closeNetwork();
		}
	}*/

	// -----------------------------------------------------------------
	// If the network isn't closed already, close it.
	// -----------------------------------------------------------------
	private void closeNetwork()
	{	if(pingThread!=null) { pingThread.quit=true;  pingThread.interrupt(); }
		if(network!=null)
			try { network.close();  network=null; }catch(IOException e) {}
	}

	// -----------------------------------------------------------------
	// Are we logged into Yahoo?
	// -----------------------------------------------------------------
	private void checkStatus() throws IllegalStateException
	{	if(sessionStatus!=MESSAGING)
			throw new IllegalStateException("Not logged in");
	}

	private void checkChatStatus() throws IllegalStateException
	{	if(chatSessionStatus!=MESSAGING)
			throw new IllegalStateException("Not logged in to a chatroom");
	}

	// -----------------------------------------------------------------
	// Identities array utility code
	// -----------------------------------------------------------------
	private YahooIdentity identityIdToObject(String yid)
	{	for(int i=0;i<identities.length;i++)
			if(yid.equals(identities[i].getId()))  return identities[i];
		return null;
	}
	
	private void checkIdentity(YahooIdentity yid) throws IllegalIdentityException
	{	for(int i=0;i<identities.length;i++)
			if(yid==identities[i])  return;
		throw new IllegalIdentityException(yid+" not a valid identity for this session");
	}
	// FIX: 
	private void checkIdentityNotOnList(String[] yids)
	{	for(int i=0;i<yids.length;i++)
		{	if(identityIdToObject(yids[i]) != null)
				throw new IllegalIdentityException(yids[i]+" is an identity of this session and cannot be used here");
		}				
	}


	// -----------------------------------------------------------------
	// Preform a clean up of all data fields to 'reset' instance
	// -----------------------------------------------------------------
	private void resetData()
	{	primaryID=null;  loginID=null;  password=null;
		cookieY=null;  cookieT=null; cookieC=null;
		imvironment=null;
		customStatusMessage=null;  customStatusBusy=false;
		groups=null;  identities=null;
		clearTypingNotifiers();
		loginOver=false;  loginException=null;
		chatConnectOver=false;  chatLoginOver=false;
	}

	// -----------------------------------------------------------------
	// Shutdown and clear all typing notification threads
	// -----------------------------------------------------------------
	private void clearTypingNotifiers()
	{	synchronized(typingNotifiers)
		{	for(Enumeration e=typingNotifiers.keys();e.hasMoreElements();)
			{	String key = (String)e.nextElement();
				TypingNotifier tn = (TypingNotifier)typingNotifiers.get(key);
				tn.quit=true;  tn.interrupt();  typingNotifiers.remove(key);
			}
		}
	}

	// -----------------------------------------------------------------
	// A key 16 was received, send an error message event
	// -----------------------------------------------------------------
	private void errorMessage(YMSG9Packet pkt,String m)
	{	if(m==null)  m=pkt.getValue("16");
		SessionErrorEvent se = new SessionErrorEvent(this,m,pkt.service);
		if(pkt.exists("114"))
			se.setCode( Integer.parseInt(pkt.getValue("114").trim()) );
		new FireEvent().fire(se,SERVICE_X_ERROR);
	}
	
	// -----------------------------------------------------------------
	// Chat logins sometimes use multiple packets.  The clue is that incomplete
	// packets carry a status of 5, and the final packet carries a status of 1.
	// This method compounds incoming 0x98 packets and returns null until the
	// last ('1') packet is delivered, when it returns the compounded packet.
	// -----------------------------------------------------------------
	private YMSG9Packet compoundChatLoginPacket(YMSG9Packet pkt)
	{	if(pkt.status==STATUS_INCOMPLETE)
		{	if(cachePacket==null)				// First of multiple
			{	cachePacket=pkt;
			}
			else								// 2nd...final-1 of multiple
			{	cachePacket.append(pkt);
			}
			return null;
		}
		else if(pkt.status==STATUS_COMPLETE)
		{	if(cachePacket!=null)				// Final of multiple
			{	cachePacket.append(pkt);
				pkt=cachePacket;  cachePacket=null;
			}
			//else { NOP }						// Final and only
			return pkt;
		}
		else	// Should never happen!
		{	return pkt;
		}
	}

	// -----------------------------------------------------------------
	// LOGON packets can contain multiple friend status sections,
	// ISAWAY and ISBACK packets contain only one.  Update the YahooUser
	// details and fire event.
	// -----------------------------------------------------------------

    private void updateFriendsStatus(YMSG9Packet pkt)
    {
        String s = pkt.getValue("8");
        if(s == null && pkt.getValue("7") != null)
            s = "1";
        boolean logoff = pkt.service == 2;
        if(s != null)
        {
            int cnt = Integer.parseInt(s);
            SessionFriendEvent se = new SessionFriendEvent(this, cnt);
            for(int i = 0; i < cnt; i++)
            {
                YahooUser yu = userStore.get(pkt.getNthValue("7", i));
                if(yu == null)
                {
                    String n = pkt.getNthValue("7", i);
                    yu = userStore.getOrCreate(n);
                }
                if(pkt.exists("17"))
                    yu.update(pkt.getNthValue("7", i), logoff ? "1515563606" : pkt.getNthValue("10", i), pkt.getNthValue("17", i), pkt.getNthValue("13", i));
                else
                    yu.update(pkt.getNthValue("7", i), logoff ? "1515563606" : pkt.getNthValue("10", i), pkt.getNthValue("13", i));
                if(pkt.getNthValue("19", i) != null && pkt.getNthValue("47", i) != null)
                    yu.setCustom(pkt.getNthValue("19", i), pkt.getNthValue("47", i));
                se.setUser(i, yu);
            }

            (new FireEvent()).fire(se, 3);
        }
    }

/*	private void updateFriendsStatus(YMSG9Packet pkt)
	{	// -----Online friends count, however count may be missing if == 1
		// -----(Note: only LOGON packets have multiple friends)
		String s = pkt.getValue("8");
		if(s==null && pkt.getValue("7")!=null)  s="1";
		// -----If LOGOFF packet, the packet's user status is wrong (available)
		boolean logoff=(pkt.service==SERVICE_LOGOFF);
		// -----Process online friends data
		if(s!=null)
		{	int cnt = Integer.parseInt(s);
			SessionFriendEvent se = new SessionFriendEvent(this,cnt);
			// -----Process each friend
			for(int i=0;i<cnt;i++)
			{	// -----Update user (do not create new user, as client may
				// -----still have reference to old
				YahooUser yu = userStore.get(pkt.getNthValue("7",i));
				// -----When we add a friend, we get a status update before
				// -----getting a confirmation FRIENDADD packet (crazy!)
				if(yu==null)
				{	String n = pkt.getNthValue("7",i);
					yu = userStore.getOrCreate(n);
				}
				// ----- 7=friend 10=status 17=chat 13=pager
				yu.update
				(	pkt.getNthValue("7",i) ,
					logoff ? STATUS_OFFLINE+"" : pkt.getNthValue("10",i) ,
					pkt.getNthValue("17",i) ,
					pkt.getNthValue("13",i)
				);
				// -----Custom message?
				if(pkt.getNthValue("19",i)!=null && pkt.getNthValue("47",i)!=null)
				{	yu.setCustom(pkt.getNthValue("19",i),pkt.getNthValue("47",i));
				}
				// -----Add to event object
				se.setUser(i,yu);
			}
			// -----Fire event
			new FireEvent().fire(se,SERVICE_ISAWAY);
		}
	}
*/
	// -----------------------------------------------------------------
	// Inserts the given user into the desired group, if not already
	// present.  Creates the group if not present.
	// -----------------------------------------------------------------
	private void insertFriend(YahooUser yu,String gr)
	{	int idx;
		// -----Find index for group
		for(idx=0;idx<groups.length;idx++)
			if(groups[idx].getName().equalsIgnoreCase(gr))  break;
		// -----Group not found?  Create!
		if(idx>=groups.length)
		{	YahooGroup[] arr = new YahooGroup[groups.length+1];
			int j=0,k=0;
			while(j<groups.length && groups[j].getName().compareTo(gr)<0)
			{	arr[j]=groups[j];  j++;
			}
			idx=j;  arr[idx] = new YahooGroup(gr);
			while(j<groups.length)
			{	arr[j+1]=groups[j];  j++;
			}
			groups=arr;
		}
		// -----Add user if needs be
		if(groups[idx].getIndexOfFriend(yu.getId())<0)
		{	groups[idx].addUser(yu);  yu.adjustGroupCount(+1);
		}
	}

	// -----------------------------------------------------------------
	// Deletes a friend from the desired group, if present.  Removes the
	// group too if now empty.
	// -----------------------------------------------------------------
	private void deleteFriend(YahooUser yu,String gr)
	{	int idx,j;
		// -----Find index for group
		for(idx=0;idx<groups.length;idx++)
			if(groups[idx].getName().equalsIgnoreCase(gr))  break;
		if(idx>=groups.length)  return;
		// -----Find index of friend and remove
		j=groups[idx].getIndexOfFriend(yu.getId());
		if(j<0)  return;
		groups[idx].removeUserAt(j);  yu.adjustGroupCount(-1);
		// -----If the groups is empty, remove it too
		if(groups[idx].isEmpty())
		{	YahooGroup[] arr = new YahooGroup[groups.length-1];
			for(j=0;j<idx;j++)  arr[j]=groups[j];
			for(j=idx;j<arr.length;j++)  arr[j]=groups[j+1];
			groups=arr;
		}
	}

	// -----------------------------------------------------------------
	// Create chat user from a chat packet.  Note: a YahooUser is create
	// if necessary.
	// -----------------------------------------------------------------
	private YahooChatUser createChatUser(YMSG9Packet pkt,int i)
	{	YahooUser yu = userStore.getOrCreate( pkt.getNthValue("109",i) );
		return new YahooChatUser
		(	yu ,										// Yahoo id
			pkt.getValueFromNthSet("109","113",i) ,		// Attributes
			pkt.getValueFromNthSet("109","141",i) ,		// Alias (optional)
			pkt.getValueFromNthSet("109","110",i) ,		// Age (or zero)
			pkt.getValueFromNthSet("109","142",i)		// Location (optional)
		);
	}

	// -----------------------------------------------------------------
	// Create a unique conference name
	// -----------------------------------------------------------------
	private String getConferenceName(String yid)
	{	return yid+"-"+conferenceCount++;
	}

	private YahooConference getConference(String room) throws NoSuchConferenceException
	{	YahooConference yc = (YahooConference)conferences.get(room);
		if(yc==null)  throw new NoSuchConferenceException("Conference "+room+" not found.");
			else  return yc;
	}

	private YahooConference getOrCreateConference(YMSG9Packet pkt)
	{	String room = pkt.getValue("57");
		YahooIdentity yid = identityIdToObject(pkt.getValue("1"));
		YahooConference yc = (YahooConference)conferences.get(room);
		if(yc==null) { yc = new YahooConference(userStore,yid,room,this);  conferences.put(room,yc); }
		return yc;
	}



	// *****************************************************************
	// Thread for handling network input, dispatching incoming packets to
	// appropriate methods based upon service id.
	// *****************************************************************
	private class InputThread extends Thread
	{	public boolean quit=false;				// Exit run in J2 compliant way

		// -----Start input thread
		public InputThread() { super(ymsgThreads,"Network Input");  this.start(); }

		// -----Accept packets and send them for processing
		public void run()
		{	try
			{	// -----Dies when (a) a LOGOFF packet sets quit, or (b) a null
				// -----packet is sent to process().
				while(!quit)
				{	try
					{	process(network.receivePacket());
					}catch(Exception e)
					{	try
						{	SessionExceptionEvent se = new SessionExceptionEvent(Session.this,"Source: InputThread",e);
							new FireEvent().fire(se,SERVICE_X_EXCEPTION);
						}catch(Exception e2) { e2.printStackTrace(); }
					}
				}
			}
			finally
			{	// -----Terminate (note: network may already have been closed if
				// -----the loop terminated due to a lost connection).
				closeNetwork();
				new FireEvent().fire(new SessionEvent(this),SERVICE_LOGOFF);
			}
		}

		// -----Switch on packet type to handler code
		void process(YMSG9Packet pkt) throws Exception
		{	// -----A null packet is sent when the input stream closes
			if(pkt == null) { quit=true;  return; }
			// -----Process header
			if(pkt.sessionId!=0)				// Some chat packets send zero
				sessionId = pkt.sessionId;		// Update sess id in outer class
			// -----Error header?
			if(pkt.status==-1 && processError(pkt)==true) return;
			// -----Process payload
			switch(pkt.service)				// Jump to service-specific code
			{	case SERVICE_ADDIGNORE :	receiveAddIgnore(pkt);  break;
				case SERVICE_AUTH : 		receiveAuth(pkt);  break;
				case SERVICE_AUTHRESP :		receiveAuthResp(pkt);  break;
				case SERVICE_CHATCONNECT : 	receiveChatConnect(pkt);  break;
				case SERVICE_CHATDISCONNECT:receiveChatDisconnect(pkt);  break;
				case SERVICE_CHATLOGOFF : 	receiveChatLogoff(pkt);  break;
				case SERVICE_CHATLOGON :	receiveChatLogon(pkt);  break;
				case SERVICE_CHATMSG : 		receiveChatMsg(pkt);  break;
				case SERVICE_CHATPM :		receiveChatPM(pkt);  break;
				case SERVICE_CONFADDINVITE:	receiveConfAddInvite(pkt);  break;
				case SERVICE_CONFDECLINE :	receiveConfDecline(pkt);  break;
				case SERVICE_CONFINVITE :	receiveConfInvite(pkt);  break;
				case SERVICE_CONFLOGOFF :	receiveConfLogoff(pkt);  break;
				case SERVICE_CONFLOGON :	receiveConfLogon(pkt);  break;
				case SERVICE_CONFMSG :		receiveConfMsg(pkt);  break;
				case SERVICE_CONTACTIGNORE:	receiveContactIgnore(pkt);  break;
				case SERVICE_CONTACTNEW :	receiveContactNew(pkt);  break;
				case SERVICE_FILETRANSFER : receiveFileTransfer(pkt);  break;
				case SERVICE_FRIENDADD :	receiveFriendAdd(pkt);  break;
				case SERVICE_FRIENDREMOVE :	receiveFriendRemove(pkt);  break;
				case SERVICE_IDACT :		receiveIdAct(pkt);  break;
				case SERVICE_IDDEACT :		receiveIdDeact(pkt);  break;
				case SERVICE_ISAWAY :		receiveIsAway(pkt);  break;
				case SERVICE_ISBACK :		receiveIsBack(pkt);  break;
				case SERVICE_LIST : 		receiveList(pkt);  break;
				case SERVICE_LOGOFF :		receiveLogoff(pkt);  break;
				case SERVICE_LOGON :		receiveLogon(pkt);  break;
				case SERVICE_MESSAGE :		receiveMessage(pkt);  break;
				case SERVICE_NEWMAIL :		receiveNewMail(pkt);  break;
				case SERVICE_NOTIFY :		receiveNotify(pkt);  break;
				case SERVICE_PING :			receivePing(pkt);  break;
				case SERVICE_USERSTAT :		receiveUserStat(pkt);  break;
				default : System.out.println("UNKNOWN: "+pkt.toString());  break;
			}
		}

		// -----Called when status == -1.  Returns true if no further processing is
		// -----required (process() returns) otherwise false (process() continues).
		boolean processError(YMSG9Packet pkt) throws Exception
		{	switch(pkt.service)				// Jump to service-specific code
			{	case SERVICE_AUTHRESP :		receiveAuthResp(pkt);  return true;
				case SERVICE_CHATLOGON :	receiveChatLogon(pkt);  return true;
				case SERVICE_LOGOFF :		receiveLogoff(pkt);  return true;
				default : errorMessage(pkt,null);  return (pkt.body.length<=2);
			}
		}
	}

	// *****************************************************************
	// Thread for sending ping packets when needed
	// Client sends a ping packet to the server every now and again.
	// *****************************************************************
	private class PingThread extends Thread
	{	public boolean quit=false;					// Exit run in J2 compliant way
		public int time = 1000*60*20;				// 20 minutes

		public PingThread()
		{	super(ymsgThreads,"Ping");
			this.setPriority(Thread.MIN_PRIORITY);  this.start();
		}

		public void run()
		{	try { Thread.sleep(time); } catch(InterruptedException e) {}
			while(!quit)
			{	try
				{	transmitPing();
					if(currentLobby!=null)  transmitChatPing();
					try { Thread.sleep(time); } catch(InterruptedException e) {}
				}catch(Exception e) {}
			}
		}
	}

	// *****************************************************************
	// Thread for sending typing start/stop packets
	// There are two key components here: The first is the keyPressed()
	// method which timestamps the last keypress from its source AWT
	// component and sends typing start packets when needed.  The second
	// is a thread which wakes infrequently to check if the last timestamp
	// is older than the timeout, and sends a typing end packet if so.
	// *****************************************************************
	private class TypingNotifier extends java.awt.event.KeyAdapter implements Runnable
	{	public boolean quit=false;					// Exit run in J2 compliant way
		private long lastKey;						// Timestamp of last keypress
		private int timeout = 1000*30;				// 30 second timeout
		private boolean typing=false;				// Current typing mode (t=yes)
		private Thread thread;						// Timeout monitoring thread
		private Component typeSource;				// Source of typing events
		private int listenerCnt=0;					// Count how often we've been added
		private String target;						// Yahoo id of target
		private String identity;					// Yahoo id of sender

		public TypingNotifier(Component com,String to,String from)
		{	typeSource=com;  target=to;  identity=from;
			if(typeSource!=null)  typeSource.addKeyListener(this);
			thread = new Thread(ymsgThreads,this,"Typing Notification: "+from+"->"+to);
			thread.setPriority(Thread.MIN_PRIORITY);
			thread.start();
		}

		// -----KeyListener method
		public void keyTyped(KeyEvent ev) { keyTyped(); }

		// -----Process a typed key
		void keyTyped()
		{	// -----Just incase we get an event before the constructor finished
			// -----or (more likely) we are actually connected to the network
			if(!thread.isAlive() || sessionStatus!=MESSAGING)  return;
			// -----Store time of key press and sent message is needed
			lastKey = System.currentTimeMillis();
			if(!typing)
			{	try { transmitNotify(target,identity,true," ",NOTIFY_TYPING); }catch(IOException e){}
			}
			typing=true;
		}

		// -----Thread to detect typing off (timeout)
		public void run()
		{	try
			{	while(!quit)
				{	// -----Wake every second and check if typing ended
					try { Thread.sleep(1000); } catch(InterruptedException e) {}
					// -----Currently typing, and timed out?  (And connected?)
					if( sessionStatus==MESSAGING && typing &&
						System.currentTimeMillis()-lastKey > timeout )
					{	try { transmitNotify(target,identity,false," ",NOTIFY_TYPING); }catch(IOException e){}
						typing=false;
					}
				}
			}
			finally
			{	if(typeSource!=null)  typeSource.removeKeyListener(this);
			}
		}

		public void interrupt() { thread.interrupt(); }

		public void stopTyping()
		{	if(typing)
			{	try { transmitNotify(target,identity,false," ",NOTIFY_TYPING); }catch(IOException e){}
				typing=false;
			}
		}
	}

	// *****************************************************************
	// Thread for firing events to listeners.  This is threaded so the
	// network code which instigates these events can return to listening
	// for input, and not get tied up in each listener's event handler.
	// *****************************************************************
	private class FireEvent extends Thread
	{	int type;
		SessionEvent ev;

		// -----Convenience methods
		FireEvent() { super(ymsgThreads,"Event Fired"); }
		void fire(SessionEvent ev,int t) { this.ev=ev;  type=t; start(); }
		public void start() { if(listeners.size()>0) super.start(); }

		// -----Thread which calls event handlers
		public void run()
		{	for(int i=0;i<listeners.size();i++)
			{	SessionListener l = (SessionListener)listeners.elementAt(i);
				//System.out.println("@@@Entered "+ev);
				switch(type)
				{	case SERVICE_LOGOFF :		l.connectionClosed(ev);  break;
					case SERVICE_ISAWAY : 		l.friendsUpdateReceived((SessionFriendEvent)ev); break;
					case SERVICE_MESSAGE : 		l.messageReceived(ev); break;
					case SERVICE_X_OFFLINE : 	l.offlineMessageReceived(ev);  break;
					case SERVICE_NEWMAIL : 		l.newMailReceived((SessionNewMailEvent)ev);  break;
					case SERVICE_CONTACTNEW :	l.contactRequestReceived((SessionEvent)ev);  break;
					case SERVICE_CONFDECLINE :	l.conferenceInviteDeclinedReceived((SessionConferenceEvent)ev);  break;
					case SERVICE_CONFINVITE :	l.conferenceInviteReceived((SessionConferenceEvent)ev);  break;
					case SERVICE_CONFLOGON :	l.conferenceLogonReceived((SessionConferenceEvent)ev);  break;
					case SERVICE_CONFLOGOFF :	l.conferenceLogoffReceived((SessionConferenceEvent)ev);  break;
					case SERVICE_CONFMSG :		l.conferenceMessageReceived((SessionConferenceEvent)ev);  break;
					case SERVICE_FILETRANSFER : l.fileTransferReceived((SessionFileTransferEvent)ev);  break;
					case SERVICE_NOTIFY : 		l.notifyReceived((SessionNotifyEvent)ev);  break;
					case SERVICE_LIST : 		l.listReceived(ev);  break;
					case SERVICE_FRIENDADD :	l.friendAddedReceived((SessionFriendEvent)ev);  break;
					case SERVICE_FRIENDREMOVE :	l.friendRemovedReceived((SessionFriendEvent)ev);  break;
					case SERVICE_CONTACTREJECT:	l.contactRejectionReceived((SessionEvent)ev);  break;
					case SERVICE_CHATLOGON :	l.chatLogonReceived((SessionChatEvent)ev);  break;
					case SERVICE_CHATLOGOFF :	l.chatLogoffReceived((SessionChatEvent)ev);  break;
					case SERVICE_CHATDISCONNECT:l.chatConnectionClosed((SessionEvent)ev);  break;
					case SERVICE_CHATMSG :		l.chatMessageReceived((SessionChatEvent)ev);  break;
					case SERVICE_X_CHATUPDATE :	l.chatUserUpdateReceived((SessionChatEvent)ev); break;
					case SERVICE_X_ERROR :		l.errorPacketReceived((SessionErrorEvent)ev); break;
					case SERVICE_X_EXCEPTION :	l.inputExceptionThrown((SessionExceptionEvent)ev); break;
					case SERVICE_X_BUZZ :		l.buzzReceived(ev);  break;
					default :					System.out.println("UNKNOWN event: "+type);  break;
				}
				//System.out.println("@@@Exited "+ev);
			}
		}
	}

	// -----------------------------------------------------------------
	// Test code
	// -----------------------------------------------------------------
	public static void dump(Session s)
	{	YahooGroup[] yg = s.getGroups();
		for(int i=0;i<yg.length;i++)
		{	System.out.print(yg[i].getName()+": ");
			Vector v = yg[i].getMembers();
			for(int j=0;j<v.size();j++)
			{	YahooUser yu = (YahooUser)v.elementAt(j);
				System.out.print(yu.getId()+" ");
			}
			System.out.print("\n");
		}

		Hashtable h = s.userStore.getUsers();
		for(Enumeration e=h.keys();e.hasMoreElements();)
		{	String k = (String)e.nextElement();
			YahooUser yu = (YahooUser)h.get(k);
			System.out.println(k+" = "+yu.getId());
		}

		YahooIdentity[] ya = s.getIdentities();
		for(int i=0;i<ya.length;i++)
			System.out.print(ya[i].getId()+" ");
		System.out.print("\n");
	}
}
