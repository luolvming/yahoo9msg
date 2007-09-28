package ymsg.test;

import ymsg.network.*;
import ymsg.network.event.*;
import ymsg.support.MessageDecoder;
import java.awt.*;
import java.awt.event.*;
import java.util.Vector;
import java.util.StringTokenizer;
import java.io.IOException;

// *********************************************************************
// This is the test client for the jYMSG9 Java API for accessing
// Yahoo IM.  This code performs two purposes:
// 	(a) It acts as a brief (incomplete) demonstration of how to use
//		the API.
//	(b) It acts as a test harness for the API, allowing each function
//		to be tested by API developers.
//
// Note: this code is for demonstration and test purposes only - it
// is not a fully featured Yahoo client, nor is it idiot proof.  It
// can, and will, throw exceptions or even crash if used incorrectly.
// Error checking is limited - if you are uncertain how to use a
// specific feature of the API (ie: what data to enter in the text
// fields) consult the source below, coupled with the API Javadocs!
// *********************************************************************
public class TestClient extends Frame implements ActionListener, ItemListener
{	protected TextField[] inputTF;
	protected Label[] inputLab;
	protected TextArea outputTA;
	protected List modeL;
	protected Button sendB,dumpB,lobbyB,confB;

	protected Vector conferences;
	protected YahooChatCategory rootCategory=null;
	protected YahooChatLobby currentLobby=null;
	protected YahooConference currentConf=null;
	protected MessageDecoder decoder;

	// The various functions of the Yahoo API are selectable from
	// a menu.  When the selected item changes, the text fields
	// used to enter parameters have their labels changed to reflect
	// the nature of the data.

	// Labels for parameter text fields (dash indicates disabled)
	private final static String[][] LABELS =
	{	{	"To", 		"Message", 	"-", 		"-"	},	// Message
		{	"To", 		"-", 		"-", 		"-"	},	// Buzz
		{	"Identity",	"-",		"-",		"-"	},	// Set identity
		{	"To",		"Filename",	"Message",	"-"	},	// Send file transfer
		{	"Friend",	"Group",	"-",		"-"	},	// Friend add
		{	"Friend",	"Group",	"-",		"-"	},	// Friend remove
		{	"Friend",	"Message",	"-",		"-"	},	// Friend reject
		{	"Friend",	"On/off",	"-",		"-"	},	// Friend ignore
		{	"To list",	"Message",	"-",		"-"	},	// Conference create
		{	"Room",		"-",		"-",		"-"	},	// Conference accept
		{	"Room",		"Message",	"-",		"-"	},	// Conference decline
		{	"Room",		"To",		"Message",	"-"	},	// Conference extend
		{	"Room",		"Message",	"-",		"-"	},	// Conference send msg
		{	"Room",		"-",		"-",		"-"	},	// Conference leave
		{	"Room:Lobby","-",		"-",		"-" },	// Chat login
		{	"Message",	"-",		"-",		"-" },	// Chat message
		{	"-",		"-",		"-",		"-" },	// Chat logoff
		{	"Username",	"Password",	"Connect",	"-" },	// Login
		{	"-",		"-",		"-",		"-" },	// Logoff
		{	"-",		"-",		"-",		"-" },	// Refresh
		{	"-",		"-",		"-",		"-" },	// Test 1
		{	"-",		"-",		"-",		"-" }	// Test 2
	};

	// Only three param fields are actually used right now
	private final static int PARAMS = 3;

	// Modes names (types of message which can be sent)
	private final static String[] MODES =
	{	"Send message", "Send BUZZ", "Set identity", "Send file tf",
		"Friend: add", "Friend: remove", "Friend: reject", "Friend: ignore",
		"Conference: create", "Conference: accept", "Conference: decline",
		"Conference: extend", "Conference: message", "Conference: leave",
		"Chat: login", "Chat: message", "Chat: logoff",
		"Login", "Logoff", "Refresh",
		"Test 1","Test 2"
	};

	// Each mode is given an id to make them easier to move around
	// the case statement which processes them.
	private final static int [] MAPPINGS =
	{	0x100, 0x101, 0x102, 0x103,					// Message etc.
		0x200, 0x201, 0x202, 0x203,					// Friends
		0x300, 0x301, 0x302, 0x303,  0x304, 0x305,	// Conference
		0x400, 0x401, 0x402,						// Chat
		0xf00, 0xf01, 0xf02,						// Login etc.
		0xf10, 0xf11								// Misc test code
	};

	// The contents of the param text fields is cached for each mode,
	// so that each mode has its params remembered.  The login mode
	// is slightly special, in that its params do not start empty -
	// they can be initialised by command line options.
	private String[][] inputCache;
	private int currentMode;

	// Where to store incoming file transfers
	private static final String FT_DIR = "/tmp";  // File transfer destination

	// The session object - our way into the Yahoo API
	protected Session session;

	// -----------------------------------------------------------------
	// CONSTRUCTOR
	// -----------------------------------------------------------------
	public TestClient(String un,String ps,String mode)
	{	super("jYMSG Test Client v2.01 : "+un);
		addWindowListener(new WindowHandler());

		Panel p,p2,p3;

		this.setBackground(new Color(0xCCCCCC));

		outputTA = new TextArea(12,40);  outputTA.setEditable(false);

		Panel bottomP = new Panel(new BorderLayout());

		p = new Panel(new BorderLayout());
		modeL = new List(5);  modeL.addItemListener(this);
		for(int i=0;i<MODES.length;i++)  modeL.add(MODES[i]);
		sendB = new Button("Send");  sendB.addActionListener(this);
		p.add(modeL,BorderLayout.CENTER);
		p.add(sendB,BorderLayout.EAST);
		bottomP.add(p,BorderLayout.NORTH);

		inputLab = new Label[PARAMS];
		inputTF = new TextField[PARAMS];
		p = new Panel(new BorderLayout());
		p2 = new Panel(new GridLayout(0,1));  p.add(p2,BorderLayout.WEST);
		p3 = new Panel(new GridLayout(0,1));  p.add(p3,BorderLayout.CENTER);
		for(int i=0;i<PARAMS;i++)
		{	inputLab[i] = new Label("----------------:",Label.RIGHT); p2.add(inputLab[i]);
			inputTF[i] = new TextField("");  p3.add(inputTF[i]);
		}
		bottomP.add(p,BorderLayout.CENTER);

		p = new Panel(new GridLayout(1,0));
		dumpB = new Button("Dump");  dumpB.addActionListener(this);  p.add(dumpB);
		lobbyB = new Button("Lobby");  lobbyB.addActionListener(this);  p.add(lobbyB);
		confB = new Button("Conf.");  confB.addActionListener(this);  p.add(confB);
		bottomP.add(p,BorderLayout.SOUTH);

		setLayout(new BorderLayout());
		add(outputTA,BorderLayout.CENTER);
		add(bottomP,BorderLayout.SOUTH);

		// -----Tell the YMSG9 API we'd like debug data dumped
		System.getProperties().put("ymsg.debug","true");

		// -----Create param text field cache, and set login params
		inputCache = new String[MODES.length][PARAMS];
		int m=findMode(0xf00);
		inputCache[m][0]=un;  inputCache[m][1]=ps;  inputCache[m][2]=mode;

		pack();  show();
		modeL.select(m);  updateSelectedMode();

		// -----Used to translate IM and chat messages into HTML
		decoder = new MessageDecoder();

		conferences = new Vector();
	}


	private void setInputCache(int serv,int pos,String content)
	{	inputCache[serv][pos]=content;
		if(currentMode==serv)  inputTF[pos].setText(content);
	}

	private int findMode(int m)
	{	for(int i=0;i<MAPPINGS.length;i++)
			if(MAPPINGS[i]==m)  return i;
		return -1;
	}

	// -----------------------------------------------------------------
	// Login to Yahoo
	// -----------------------------------------------------------------
	private void attemptLogin() throws Exception
	{	int m=findMode(0xf00);
		String username = inputCache[m][0];
		String password = inputCache[m][1];
		//System.out.println(">>"+username+" "+password);
		// -----Login to Yahoo
		try
		{	
			//System.out.println("debug1:looy");
			session.login(username,password);
			//System.out.println("debug2:looy");
		}
		catch(AccountLockedException e)
		{	System.out.println("Your account is locked");
			if(e.getWebPage()!=null)  System.out.println("Please visit: "+e.getWebPage().toString());
			throw e;
		}
		catch(LoginRefusedException e)
		{	System.out.println("Yahoo refused our connection.  Username/password incorrect?");
			throw e;
		}

		// -----Are we cooking with gas?
		if(session.getSessionStatus()==StatusConstants.MESSAGING)
		{	System.out.println(session.getConnectionHandler().toString());
		}
		else
		{	System.out.println("Sorry, there was a problem connecting");
		}
	}

	// -----------------------------------------------------------------
	// Mode changed, store current param text fields and load new ones
	// -----------------------------------------------------------------
	protected void updateSelectedMode()
	{	// -----Download current mode params to cache
		for(int i=0;i<PARAMS;i++)  inputCache[currentMode][i] = inputTF[i].getText();
		// -----Change current mode
		currentMode = modeL.getSelectedIndex();
		if(currentMode<0)  currentMode=0;
		// -----Update labels for text params
		for(int i=0;i<PARAMS;i++)
		{	inputLab[i].setText( LABELS[currentMode][i]+" :" );
			inputTF[i].setEnabled( !(LABELS[currentMode][i].equals("-")) );
		}
		// -----Upload current mode params to text fields
		for(int i=0;i<PARAMS;i++) inputTF[i].setText(inputCache[currentMode][i]);
	}

	// -----------------------------------------------------------------
	// AWT button event handler
	// -----------------------------------------------------------------
	public void actionPerformed(ActionEvent ev)
	{	Object src = ev.getSource();
		String[] t = new String[inputTF.length];
		for(int i=0;i<t.length;i++)  t[i]=inputTF[i].getText();

		try
		{	if(src==sendB)
			{	int mask=0;		// Which text fields to wipe

				//switch(MAPPINGS[modeC.getSelectedIndex()])
				switch(MAPPINGS[modeL.getSelectedIndex()])
				{	case 0x100 :
						session.sendMessage(t[0],t[1]);
						mask=0x2;  break;
					case 0x101 :
						session.sendBuzz(t[0]);
						break;
					case 0x102 :
						//session.setIdentity(t[0]);
						mask=0x1;  break;
					case 0x103 :
						session.sendFileTransfer(t[0],t[1],t[2]);
						mask=0x7;  break;

					case 0x200 :
						session.addFriend(t[0],t[1]);
						mask=0x3;  break;
					case 0x201 :
						session.removeFriend(t[0],t[1]);
						mask=0x3;  break;
					/*case 0x202 :
						session.rejectContact(t[0],t[1]);
						mask=0x3;  break;*/
					case 0x203 :
						session.ignoreContact(t[0],(t[1].equalsIgnoreCase("on")));
						mask=0x3;  break;

					case 0x300 :
						StringTokenizer st = new StringTokenizer(t[0],",");
						String[] ta = new String[st.countTokens()];
						for(int i=0;i<ta.length;i++)  ta[i] = st.nextToken().trim();
						Object o = session.createConference(ta,t[1]);
						if(!conferences.contains(o))  conferences.addElement(o);
						mask=0x3;  break;
					case 0x301 :
						session.acceptConferenceInvite(currentConf);
						mask=0x1;  break;
					case 0x302 :
						session.declineConferenceInvite(currentConf,t[1]);
						mask=0x3;  break;
					case 0x303 :
						session.extendConference(currentConf,t[1],t[2]);
						mask=0x6;  break;
					case 0x304 :
						session.sendConferenceMessage(currentConf,t[1]);
						mask=0x2;  break;
					case 0x305 :
						session.leaveConference(currentConf);
						mask=0x1;  break;

					case 0x400 :
						if(currentLobby!=null)
							session.chatLogin(currentLobby);
						mask=0x0;  break;
					case 0x401 :
						outputTA.append("["+currentLobby.getNetworkName()+"]\n  "+
							session.getPrimaryIdentity()+" : "+t[0]+"\n");
						session.sendChatMessage(t[0]);
						mask=0x1;  break;
					case 0x402 :
						session.chatLogout();
						mask=0x0;  break;

					case 0xf00 :
						updateSelectedMode();  new LoginThread();
						mask=0x2;  break;
					case 0xf01 :
						session.logout();
						mask=0x0;  break;
					case 0xf02 :
						session.refreshFriends();
						mask=0x0;  break;

					case 0xf10 :
						session.__test1(t[0],t[1]);
						mask=0x0;  break;
					case 0xf11 :
						session.__test2();
						mask=0x0;  break;

					default :
						System.err.println("Unknown option selected");
				}

				// -----Wipe specified text fields
				for(int i=0;i<PARAMS;i++)
				{	if((mask&1) > 0)  inputTF[i].setText("");
					mask=mask>>1;
				}
			}
			else if(src==dumpB) { dump(); }
			else if(src==lobbyB) { new LobbyChooser(); }
			else if(src==confB) { new ConfChooser(); }
		}catch(Exception e) { e.printStackTrace(); }
	}

	// -----------------------------------------------------------------
	// Choice listener method
	// -----------------------------------------------------------------
	public void itemStateChanged(ItemEvent ev)
	{	updateSelectedMode();
	}

	// *****************************************************************
	// YMSG9 session handler
	// *****************************************************************
	class SessionHandler extends SessionAdapter
	{	public void messageReceived(SessionEvent ev)
		{	outputTA.append(ev.getFrom()+" : "+decoder.decodeToText(ev.getMessage())+"\n");
		}
		public void errorPacketReceived(SessionErrorEvent ev)
		{	if(ev.getService()!=ServiceConstants.SERVICE_CONTACTIGNORE)
			{	outputTA.append("ERROR : "+ev.getMessage()+"\n");
				System.err.println(ev.toString());
			}
		}
		public void inputExceptionThrown(SessionExceptionEvent ev)
		{	outputTA.append("ERROR : "+ev.getMessage()+"\n");
			System.err.println(ev.toString());
		}
		public void offlineMessageReceived(SessionEvent ev)
		{	outputTA.append("At "+ev.getTimestamp().toString()+"\n");
			outputTA.append(ev.getFrom()+" : "+decoder.decodeToText(ev.getMessage())+"\n");
		}
		public void fileTransferReceived(SessionFileTransferEvent ev)
		{	messageReceived(ev);
			System.out.println(ev.getLocation().toString());
			try { session.saveFileTransferTo(ev,FT_DIR); } catch(Exception e) { e.printStackTrace(); }
		}
		public void connectionClosed(SessionEvent ev)
		{	TestClient.this.setVisible(false);  TestClient.this.dispose();
		}
		public void listReceived(SessionEvent ev)
		{	dump();
		}
		public void friendsUpdateReceived(SessionFriendEvent ev)
		{	YahooUser[] l = ev.getFriends();
			for(int i=0;i<l.length;i++)
				System.out.println("Updated: "+l[i].toString());
		}
		public void friendAddedReceived(SessionFriendEvent ev)
		{	System.out.println(ev.toString());
		}
 		public void friendRemovedReceived(SessionFriendEvent ev)
		{	System.out.println(ev.toString());
		}
        public void conferenceInviteReceived(SessionConferenceEvent ev)
		{	System.out.println(ev.toString());
			try
			{	session.declineConferenceInvite(ev.getRoom(),"Sorry!");
			}catch(IOException e) {}
		}
        public void conferenceLogonReceived(SessionConferenceEvent ev)
		{	System.out.println(ev.toString());
		}
        public void conferenceLogoffReceived(SessionConferenceEvent ev)
		{	System.out.println(ev.toString());
		}
        public void conferenceMessageReceived(SessionConferenceEvent ev)
		{	System.out.println(ev.toString());
		}
		public void chatLogonReceived(SessionChatEvent ev)
		{	outputTA.append("["+ev.getLobby().getNetworkName()+"]\n  "+ev.getChatUser().getId()+" joined\n");
		}
		public void chatLogoffReceived(SessionChatEvent ev)
 		{	outputTA.append("["+ev.getLobby().getNetworkName()+"]\n  "+ev.getChatUser().getId()+" has left\n");
		}
		public void chatMessageReceived(SessionChatEvent ev)
		{	outputTA.append("["+ev.getLobby().getNetworkName()+"]\n  "+ev.getChatUser().getId()+" : "+decoder.decodeToText(ev.getMessage())+"\n");
		}
	}

	// *****************************************************************
	// Current chat lobby
	// *****************************************************************
	class LobbyChooser extends Thread
	{	LobbyChooser() { this.start(); }
		public void run()
		{	int r;
			Selector sel;

			try
			{	// -----If necessary, load category data
				if(rootCategory==null)
					rootCategory = YahooChatCategory.loadCategories(session);

				// -----Navigate down category tree
				YahooChatCategory ycc = rootCategory;
				String title="<Root>";
				while(ycc.getCategories().size() > 0)
				{	sel = new Selector(ycc.getCategories(),title);
					r=sel.getSelected();
					ycc = (YahooChatCategory)(ycc.getCategories().elementAt(r));
					title=title+"->["+ycc.getName()+"]";
				}
				// -----Select a room from category
				sel = new Selector(ycc.getPublicRooms(),"Public rooms");
				r=sel.getSelected();
				YahooChatRoom ycr = (YahooChatRoom)(ycc.getPublicRooms().elementAt(r));
				// -----Select a lobby from room
				sel = new Selector(ycr.getLobbies(),ycr.getName()+" lobbies");
				r=sel.getSelected();
				YahooChatLobby ycl = (YahooChatLobby)(ycr.getLobbies().elementAt(r));
				// -----Make current lobby
				currentLobby=ycl;  setInputCache(findMode(0x400),0,currentLobby.getNetworkName());
				System.out.println(currentLobby.toString());
			}catch(IOException e) { e.printStackTrace(); }
		}
	}

	class ConfChooser extends Thread
	{	ConfChooser() { this.start(); }
		public void run()
		{	if(conferences.size()<=0)  return;
			Selector sel = new Selector(conferences,"Choose a conference");
			currentConf = (YahooConference)conferences.elementAt(sel.getSelected());
			int modeOffset=findMode(0x300);
			for(int i=1;i<6;i++)
				setInputCache(modeOffset+i,0,currentConf.getName());
		}
	}

	// *****************************************************************
	// Util class, select params which are not text
	// *****************************************************************
	class Selector extends Frame implements ActionListener
	{	java.awt.List selectorL;
		Vector list;
		int selected;
		String title;

		Selector(Vector v,String t)
		{	super("Selector");
			list=v;  title=t;  selected=-1;
			initGfx();
		}

		int getSelected()
		{	while(selected<0)
			{	try { Thread.sleep(100); }catch(InterruptedException e) {}
			}
			setVisible(false);  dispose();
			return selected;
		}

		void initGfx()
		{	selectorL = new java.awt.List(20);
			selectorL.addActionListener(this);
			selectorL.setMultipleMode(false);
			for(int i=0;i<list.size();i++)  selectorL.add(list.elementAt(i).toString());

			setLayout(new BorderLayout());
			add(selectorL,BorderLayout.CENTER);
			add(new Label("Select an item from the list: "+title),BorderLayout.NORTH);
			pack();  show();
		}

		public void actionPerformed(ActionEvent ev)
		{	selected = selectorL.getSelectedIndex();
		}
	}

	// *****************************************************************
	// Spawm login off into separate thread
	// *****************************************************************
	class LoginThread extends Thread
	{	LoginThread() { start(); }
		public void run()
		{	int m=findMode(0xf00);
			String mode = inputCache[m][2];
			// -----Set the connection handler as per command line
			if(mode.equals("socks"))
				session = new Session(new SOCKSConnectionHandler("autoproxy",1080));
			else if(mode.equals("http"))
				session = new Session(new HTTPConnectionHandler("proxy",8080));
			else if(mode.equals("direct"))
				session = new Session(new DirectConnectionHandler());
			else
				session = new Session();
			// -----Register a listener
			session.addSessionListener(new SessionHandler());
			//session.addTypingNotificationSource(inputTF,username);

			try { attemptLogin();  dump(); }catch(Exception e) { e.printStackTrace(); }
		}
	}

	class WindowHandler extends WindowAdapter
	{	public void windowClosing(WindowEvent ev)
		{	hide();  dispose();
			System.exit(0);
		}
	}

	// -----------------------------------------------------------------
	// Debug
	// -----------------------------------------------------------------
	public void dump()
	{	YahooGroup[] yg = session.getGroups();
		for(int i=0;i<yg.length;i++)
		{	System.out.println(yg[i].getName()+":");
			Vector v = yg[i].getMembers();
			for(int j=0;j<v.size();j++)
			{	YahooUser yu = (YahooUser)v.elementAt(j);
				System.out.println("  "+yu.toString());
			}
		}
		dumpUsers();
	}

	void dumpUsers()
	{	java.util.Hashtable h = session.getUsers();
		for(java.util.Enumeration e=h.keys();e.hasMoreElements();)
		{	String k = (String)e.nextElement();
			System.out.println(k+" ==> "+((YahooUser)h.get(k)).toString());
		}
	}

	// -----------------------------------------------------------------
	// Bootstrap
	// -----------------------------------------------------------------
	public static void main(String[] args)
	{	String un=null,ps=null,md=null;

		for(int i=0;i<args.length;i++)
		{	if(args[i].startsWith("-u:"))  un=args[i].substring(3);
			if(args[i].startsWith("-p:"))  ps=args[i].substring(3);
			if(args[i].startsWith("-m:"))  md=args[i].substring(3);
		}

		if(args.length==0)
			System.out.println("Usage: [-u:<username>] [-p:<password>] [-m:<direct|socks|http>]");
		un="dooy888";
		ps="ydl821116";
		TestClient tc = new TestClient(un,ps,md);
	}
}
