package ymsg.test;

import ymsg.network.*;
import ymsg.network.event.*;
import ymsg.support.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.text.Document;
import javax.swing.text.BadLocationException;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeCellRenderer;
import java.util.Vector;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.io.*;

// *********************************************************************
// This is the test client for the jYMSG Java API for accessing Yahoo 
// IM.  This code performs two purposes:
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
public class SwingTestClient extends JFrame implements ActionListener, ListSelectionListener
{	// -----User interface
	protected JTextField[] inputTF;						// Operand text field
	protected JLabel[] inputLab;						// Labels for operand fields
	protected JTextPane outputTP;						// Message window
	protected JList modeL;								// Mode selection list
	protected JButton sendB;							// Send message button
	protected ThreadGroup threadGroup;					// Keep our threads here
	protected Document outputDoc;						// Document for outputTP
	protected UserDisplay userDisplay=null;				// Window of confer/chat users
	protected GroupDisplay groupDisplay=null;			// Window of friends tree
	// -----The session object - our way into the Yahoo API
	protected Session session;							// Yahoo connection
	// -----Other stuff
	protected Vector conferences;						// Keep conferences here
	protected YahooChatCategory rootCategory=null;		// Chat root
	protected YahooChatLobby currentLobby=null;			// Current chat lobby
	protected YahooConference currentConf=null;			// Current conference
	protected MessageDecoder decoder;					// Yahoo message decoder
	protected AntiSpam spamBlock;						// Spam blocker
	protected SwingModelFactory factory;				// Swing models etc
	protected EmoteManager emoteManager;				// Chat emotes
	// -----Styled text demo
	protected MutableMessageElement styledOutRoot;		// Root of message
	protected MutableMessageElement styledOutBody;		// Text of message
	// -----Logs conversations
	protected PrintWriter transcript;					// Logs conversations

	// The various functions of the Yahoo API are selectable from a list.  
	// When the selected item changes, the text fields used to enter para-
	// meters have their labels changed to reflect the nature of the data.

	// Labels for parameter text fields (dash indicates disabled)
	private final static String[][] LABELS =
	{	{	"To", 		"Message", 	"-", 		"-"	},	// Message
		{	"To", 		"-", 		"-", 		"-"	},	// Buzz
		{	"To",		"Message",	"-",		"-"	},	// Styled text demo
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
		{	"To",		"Emote",	"-",		"-"	},	// Chat emote
		{	"-",		"-",		"-",		"-" },	// Chat logoff
		
		{	"-",		"-",		"-",		"-"	},	// Activate identity
		{	"-",		"-",		"-",		"-"	},	// Deactivate identity
		
		{	"To",		"-",		"-",		"-"	},	// Add typing notify
		{	"To",		"-",		"-",		"-"	},	// Remove typing notify
		{	"To",		"-",		"-",		"-"	},	// Key typed notification
		
		{	"Username",	"Password",	"Connect",	"-" },	// Login
		{	"-",		"-",		"-",		"-" },	// Logoff
		{	"-",		"-",		"-",		"-" },	// Refresh
		
		{	"To",		"Msg",		"-",		"-" },	// Test 1
		{	"-",		"-",		"-",		"-" },	// Test 2
		{	"Comment",	"-",		"-",		"-" }	// Comment
	};

	// Only three param fields are actually used right now
	private final static int PARAMS = 3;

	// Modes names (types of message which can be sent)
	private final static String[] MODES =
	{	"Send message", "Send BUZZ", "Styled text demo", "Send file tf",
		"Friend: add", "Friend: remove", "Friend: reject", "Friend: ignore",
		"Conference: create", "Conference: accept", "Conference: decline",
		"Conference: extend", "Conference: message", "Conference: leave",
		"Chat: login", "Chat: message", "Chat: Emote", "Chat: logoff",
		"Activate identity", "Deactivate identity",
		"Add typing notif.", "Remove typing notif.", "Send typing notif.",
		"Login", "Logoff", "Refresh",
		"Test 1","Test 2","Log comment"
	};

	// Each mode is given an id to make them easier to move around the case
	// statement which processes them.
	private final static int [] MAPPINGS =
	{	0x100, 0x101, 0x102, 0x103,					// Message etc.
		0x200, 0x201, 0x202, 0x203,					// Friends
		0x300, 0x301, 0x302, 0x303,  0x304, 0x305,	// Conference
		0x400, 0x401, 0x403, 0x402,					// Chat
		0x500, 0x501,								// Identities
		0x600, 0x601, 0x602,						// Typing notification
		0xf00, 0xf01, 0xf02,						// Login etc.
		0xf10, 0xf11, 0xf12							// Misc test code
	};

	// The contents of the param text fields is cached for each mode, so 
	// that each mode has its params remembered.  The login mode is 
	// slightly special, in that its params do not start empty - they can 
	// be initialised by command line options.
	private String[][] inputCache;
	private int currentMode;

	// Where to store incoming file transfers
	private static final String FT_DIR = "/tmp";  // File transfer destination

	// Menus
	private static final String[] MENUS =
	{	"Project","Functions","Chooser","Options"
	};
	private static final String[][] MENUITEMS =
	{	{	"Quit"		},
		{	"Dump","Clear","Threads","Garbage","Reset"	},
		{	"Choose lobby","Choose conf.","Choose Emote" },
		{	"+Invisible"	}
	};
	private static final String[][] MENUCMDS =
	{	{	"QUIT"		},
		{	"DUMP","CLEAR","THREADS","GARBAGE","RESET" },
		{	"LOBBY","CONF","EMOTE" },
		{	"INVIS"		}
	};
	private Hashtable menuItems;
	private JMenu identitiesMenu;
	private Hashtable identitiesHash;
	private YahooIdentity currentIdentity;


	// -----------------------------------------------------------------
	// CONSTRUCTOR
	// -----------------------------------------------------------------
	public SwingTestClient(String un,String ps,String mode,String trans)
	{	super("jYMSG Test Client v2.2 : "+un);
		addWindowListener(new WindowHandler());

		// -----Open transcript file, if -T was set on command line
		if(trans!=null)
		{	try
			{	transcript = new PrintWriter(new FileOutputStream(trans));
			}catch(IOException e) { e.printStackTrace(); }
		}

		// -----Keep all the client's threads together
		threadGroup = new ThreadGroup("Test Client Threads");
		
		// -----START: build main GUI
		JPanel p,p2,p3;

		this.setBackground(new Color(0xCCCCCC));

		outputTP = new JTextPane();  outputTP.setEditable(false);
		outputTP.setPreferredSize(new Dimension(400,250));
		outputDoc=outputTP.getDocument();

		JPanel bottomP = new JPanel(new BorderLayout());

		p = new JPanel(new BorderLayout());
		modeL = new JList(MODES);  modeL.setVisibleRowCount(5);
		modeL.addListSelectionListener(this);
		sendB = new JButton("Send");  sendB.addActionListener(this);
		p.add(new JScrollPane(modeL),BorderLayout.CENTER);
		p.add(sendB,BorderLayout.EAST);
		bottomP.add(p,BorderLayout.NORTH);

		inputLab = new JLabel[PARAMS];
		inputTF = new JTextField[PARAMS];
		p = new JPanel(new BorderLayout());
		p2 = new JPanel(new GridLayout(0,1));  p.add(p2,BorderLayout.CENTER);
		p3 = new JPanel(new GridLayout(0,1));  p.add(p3,BorderLayout.EAST);
		for(int i=0;i<PARAMS;i++)
		{	inputLab[i] = new JLabel("--------------------:",JLabel.RIGHT); p2.add(inputLab[i]);
			inputTF[i] = new JTextField("",40);  p3.add(inputTF[i]);
		}
		bottomP.add(p,BorderLayout.CENTER);

		Container c = getContentPane();
		c.setLayout(new BorderLayout());
		c.add(new JScrollPane(outputTP),BorderLayout.CENTER);
		c.add(bottomP,BorderLayout.SOUTH);
		// -----END: build main GUI

		// -----Tell the YMSG9 API we'd like debug data dumped
		System.getProperties().put("ymsg.debug","true");

		// -----Create param text field cache, and set login params
		inputCache = new String[MODES.length][PARAMS];
		int m=findMode(0xf00);
		inputCache[m][0]=un;  inputCache[m][1]=ps;  inputCache[m][2]=mode;

		// -----Menu bar
		menuItems = new Hashtable();
		JMenuBar mb = new JMenuBar();
		MenuHandler mh = new MenuHandler();
		for(int i=0;i<MENUS.length;i++)  mb.add(createMenu(i,mh));
		identitiesMenu = new JMenu("Identities");  mb.add(identitiesMenu);
		setJMenuBar(mb);

		// -----Pack and show the window
		pack();  show();
		
		// -----Get the mode selectiion list ready
		modeL.setSelectedIndex(m);  modeL.ensureIndexIsVisible(m);
		updateSelectedMode();

		// -----Used to translate IM and chat messages into styled text:  Decode
		// -----smileys; use Swing's styles by default; allow messages to change
		// -----their face or colour; but keep font size between 10 and 18.
		MessageDecoderSettings sets = new MessageDecoderSettings();
		sets.setRespectTextFade(true);
		sets.setRespectTextAlt(true);
		sets.setEmoticonsDecoded(false);
		sets.setOverrideFont(null,10,18,null);
		decoder = new MessageDecoder(sets);
		// -----Utility classes, filter, etc
		spamBlock = new AntiSpam();
		emoteManager = new EmoteManager("./emote_user.dat",true);

		// -----Used to test styled output.  See MutableMessageElement
		createStyledTextTemplates();

		// -----Store our conferences here
		conferences = new Vector();
	}

	public void finalize()
	{	if(transcript!=null)  transcript.close();
	}

	// -----------------------------------------------------------------
	// Use a clever bit of trickery to create menus without huge quantities
	// of code...
	// -----------------------------------------------------------------
	private JMenu createMenu(int i,MenuHandler mh)
	{	JMenu m = new JMenu(MENUS[i]);
		String s;
		for(int j=0;j<MENUITEMS[i].length;j++)
		{	s = MENUITEMS[i][j];
			if(s.startsWith("-"))
			{	m.addSeparator();
			}
			else
			{	JMenuItem mi;
				switch(s.charAt(0))
				{	case '.' :
						mi = new JMenuItem(s.substring(1));
						mi.setEnabled(false);
						break;
					case '+' :
						mi = new JCheckBoxMenuItem(s.substring(1));
						break;
					default :
						mi = new JMenuItem(s);
						break;
				}
				mi.setActionCommand(MENUCMDS[i][j]);
				mi.addActionListener(mh);
				m.add(mi);
				menuItems.put(MENUCMDS[i][j],mi);
			}
		}
		return m;
	}

	// -----------------------------------------------------------------
	// These objects are used to test the rich format output of
	// MutableMessageElement.  Normally an app might build these on the 
	// fly, from styled text entered by the user - but for testing 
	// purposes we'll just create some ready-made demo trees.
	// -----------------------------------------------------------------
	private void createStyledTextTemplates()
	{	// -----Root object
		styledOutRoot = MutableMessageElement.createRoot();
		// -----Add a red container to root
		MutableMessageElement el1 = MutableMessageElement.createIndexedColour(8);
		styledOutRoot.add(el1);
		// -----Add an italic container to the red container
		MutableMessageElement el2 = MutableMessageElement.createItalic();
		el1.add(el2);
		// -----Add a text element to the italic container - later on we will
		// -----change its contents for each new message
		styledOutBody = MutableMessageElement.createText("");
		el2.add(styledOutBody);
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
		{	System.out.println("Login[");
			session.login(username,password);
			System.out.println("Login]");
		}
		catch(LoginRefusedException e)
		{	switch((int)e.getStatus())
			{	case (int)StatusConstants.STATUS_BADUSERNAME :
					System.out.println("Yahoo doesn't recognise that username.");
					break;
				case (int)StatusConstants.STATUS_BAD :
					System.out.println("Yahoo refused our connection.  Password incorrect?");
					break;
				case (int)StatusConstants.STATUS_LOCKED :
					System.out.println("Your account is locked");
					AccountLockedException e2 = (AccountLockedException)e;
					if(e2.getWebPage()!=null)  System.out.println("Please visit: "+e2.getWebPage().toString());
					break;
			}
			throw e;
		}

		// -----Are we cooking with gas?
		if(session.getSessionStatus()==StatusConstants.MESSAGING)
		{	// -----Create factory for Swing models, use it to get friends tree
			if(factory==null)
				factory = new SwingModelFactory(session);
			if(groupDisplay==null || !groupDisplay.isDisplayable())
			{	GroupDisplay gd = new GroupDisplay();
				gd.setModel( factory.createTreeModel(true) );
			}
			
			// -----Update identities list
			YahooIdentity[] ids = session.getIdentities();
			identitiesHash = new Hashtable();
			ButtonGroup identitiesBG = new ButtonGroup();
			identitiesMenu.removeAll();
			MenuHandler mh = new MenuHandler();
			JRadioButtonMenuItem rbmi = _makeRBMI("[default]",mh,"IDENT_NONE",true);
			identitiesBG.add(rbmi);  identitiesMenu.add(rbmi);
			for(int i=0;i<ids.length;i++)
			{	rbmi = _makeRBMI(ids[i].getId(),mh,"IDENT"+ids[i].getId(),false);
				identitiesBG.add(rbmi);  identitiesMenu.add(rbmi);
				identitiesHash.put(ids[i].getId() , ids[i]);
			}
			currentIdentity=null;
		}
		else
		{	System.out.println("Sorry, there was a problem connecting");
		}
	}
	
	private JRadioButtonMenuItem _makeRBMI(String txt,MenuHandler mh,String act,boolean sel)
	{	JRadioButtonMenuItem rbmi = new JRadioButtonMenuItem(txt);
		rbmi.addActionListener(mh);
		rbmi.setActionCommand(act);
		rbmi.setSelected(sel);
		return rbmi;
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
		for(int i=0;i<t.length;i++)  t[i]=inputTF[i].getText().trim();

		String myId = (currentIdentity==null) ? "[default]" : currentIdentity.getId();
							
		try
		{	if(src==sendB)
			{	int mask=0;		// Which text fields to wipe

				switch(MAPPINGS[modeL.getSelectedIndex()])
				{	case 0x100 :
						if(currentIdentity!=null)
							session.sendMessage(t[0],t[1],currentIdentity);
						else 
							session.sendMessage(t[0],t[1]);
						_appendOutput(myId+"->"+t[0]+" : "+t[1]+"\n");  _pushDown();
						mask=0x2;  break;
					case 0x101 :
						if(currentIdentity!=null)  session.sendBuzz(t[0],currentIdentity);
							else  session.sendBuzz(t[0]);
						break;
					case 0x102 :
						styledOutBody.setText(t[1]);
						session.sendMessage(t[0],styledOutRoot.toYahooIM());
						mask=0x2;  break;
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
						Object o = (currentIdentity!=null) ?
							session.createConference(ta,t[1],currentIdentity) :
							session.createConference(ta,t[1]) ;
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
						{	if(currentIdentity!=null)
								session.chatLogin(currentLobby,currentIdentity);
							else
								session.chatLogin(currentLobby);
							// -----User list window
							if(factory==null)
								factory = new SwingModelFactory(session);
							if(userDisplay==null || !userDisplay.isDisplayable())
								userDisplay = new UserDisplay();
							userDisplay.setModel( factory.createListModel(currentLobby,true) );
							userDisplay.show();
						}
						mask=0x0;  break;
					case 0x401 :
						_appendOutput("["+currentLobby.getNetworkName()+"]  "+
							myId+" : "+t[0]+"\n");
						_pushDown();
						session.sendChatMessage(t[0]);
						mask=0x1;  break;
					case 0x402 :
						session.chatLogout();
						userDisplay.setModel(SwingModelFactory.getEmptyListModel());
						mask=0x0;  break;
					case 0x403 :
						String em;
						if(t[0].length()<=0)
						{	em=emoteManager.getRoomEmote(t[1]);
						}
						else
						{	em=emoteManager.getUserEmote(t[1]);
							em=EmoteManager.encodeEmote(t[0],em);
						}
						_appendOutput("["+currentLobby.getNetworkName()+"]  "+
							myId+" : <"+em+">\n");
						_pushDown();
						if(em!=null && em.length()>0)  session.sendChatEmote(em);
						mask=0x1;  break;

					case 0x500 :
						session.activateIdentity(currentIdentity,true);
						mask=0x0;  break;
					case 0x501 :
						session.activateIdentity(currentIdentity,false);
						mask=0x0;  break;
						
					case 0x600 :
						session.addTypingNotification(t[0],null);
						mask=0x3;  break;
					case 0x601 :
						session.removeTypingNotification(t[0]);
						mask=0x3;  break;
					case 0x602 :
						session.keyTyped(t[0]);
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
					case 0xf12 :
						System.out.println("COMMENT: "+t[0]);
						mask=0x1;  break;

					default :
						System.err.println("Unknown option selected");
				}

				// -----Wipe specified text fields
				for(int i=0;i<PARAMS;i++)
				{	if((mask&1) > 0)  inputTF[i].setText("");
					mask=mask>>1;
				}
			}
		}catch(Exception e) { e.printStackTrace(); }
	}

	private void _appendOutput(String s)
	{	try
		{	outputDoc.insertString(outputDoc.getLength(),s,null);
			_log(s);
			//outputTP.setCaretPostion(outputDoc.getLength());
		}catch(BadLocationException e) {}
	}
	
	private void _pushDown()
	{	try
		{	outputTP.setCaretPosition(outputTP.getText().length());
			outputTP.scrollRectToVisible( new Rectangle(0,outputTP.getSize().height,1,1) );
		}catch(Exception e) {}
	}
	
	private void _log(String s)
	{	if(transcript!=null)
		{	transcript.print(s);
		}
	}

	// -----------------------------------------------------------------
	// Choice listener method
	// -----------------------------------------------------------------
	public void valueChanged(ListSelectionEvent ev)
	{	updateSelectedMode();
	}

	// *************************************************************
	// Menu event handler
	// *************************************************************
	class MenuHandler implements ActionListener
	{	public void actionPerformed(ActionEvent ev)
		{	try
			{	String s = ev.getActionCommand();
				if(s.equals("QUIT"))  System.exit(0);
				else if(s.equals("DUMP")) { dump();  dumpChat(); }
				else if(s.equals("CLEAR")) { outputDoc.remove(0,outputDoc.getLength()); }
				else if(s.equals("LOBBY")) { new LobbyChooser(); }
				else if(s.equals("CONF")) { new ConfChooser(); }
				else if(s.equals("EMOTE")) { new EmoteChooser(); }
				else if(s.equals("THREADS"))
				{	System.out.println("Active count: "+Thread.activeCount());
					Thread[] t = new Thread[Thread.activeCount()];
					Thread.enumerate(t);
					for(int i=0;i<t.length;i++)
						System.out.println("Thread "+i+": "+t[i]);
				}
				else if(s.equals("GARBAGE")) { System.gc(); }
				else if(s.equals("RESET")) { session.reset(); }
				else if(s.equals("IDENT_NONE")) { currentIdentity=null; }
				else if(s.startsWith("IDENT")) { currentIdentity=(YahooIdentity)identitiesHash.get(s.substring(5)); }
			}catch(Exception e) { e.printStackTrace(); }
		}
	}

	// *****************************************************************
	// YMSG9 session handler
	// *****************************************************************
	class SessionHandler extends SessionAdapter
	{	public void messageReceived(SessionEvent ev)
		{	_appendOutput(ev.getFrom()+"->"+ev.getTo()+" : ");
			decoder.appendToDocument(ev.getMessage(),outputDoc);
			_pushDown();
			_log(ev.getMessage());
		}
		public void errorPacketReceived(SessionErrorEvent ev)
		{	if(ev.getService()!=ServiceConstants.SERVICE_CONTACTIGNORE)
			{	_appendOutput("ERROR : "+ev.getMessage()+"\n");  _pushDown();
				System.err.println(ev.toString());
			}
		}
		public void inputExceptionThrown(SessionExceptionEvent ev)
		{	_appendOutput("ERROR : "+ev.getMessage()+"\n");  _pushDown();
			YMSG9BadFormatException ex = (YMSG9BadFormatException)ev.getException();
			System.err.println("**Message:\n"+ev.toString());
			System.err.println("**Exception:");
			ex.printStackTrace();
			System.err.println("**Cause:");
			ex.getCausingThrowable().printStackTrace();
		}
		public void offlineMessageReceived(SessionEvent ev)
		{	_appendOutput("At "+ev.getTimestamp().toString()+"\n");
			_appendOutput(ev.getFrom()+" : "+decoder.decodeToText(ev.getMessage())+"\n");
			_pushDown();
		}
		public void fileTransferReceived(SessionFileTransferEvent ev)
		{	messageReceived(ev);
			System.out.println(ev.getLocation().toString());
			try { session.saveFileTransferTo(ev,FT_DIR); } catch(Exception e) { e.printStackTrace(); }
		}
		public void connectionClosed(SessionEvent ev)
		{	//SwingTestClient.this.hide();  SwingTestClient.this.dispose();
			_appendOutput("***Connection closed***\n");  _pushDown();
			System.out.println("**Connection closed**");
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
        public void contactRequestReceived(SessionEvent ev)
		{	// Comment out the below line if you want conacts to be accepted
			try
			{	session.rejectContact(ev,"Not now, thanks");
			}catch(IOException e) { e.printStackTrace(); }
		}
		public void conferenceInviteReceived(SessionConferenceEvent ev)
		{	System.out.println(ev.toString());
			try
			{	session.declineConferenceInvite(ev.getRoom(),"Sorry!");
			}catch(IOException e) {}
		}
        public void conferenceInviteDeclinedReceived(SessionConferenceEvent ev)
		{	System.out.println(ev.toString());
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
		{	_appendOutput("["+ev.getLobby().getNetworkName()+"]  "+ev.getChatUser().getId()+" joined\n");
			_pushDown();
		}
		public void chatLogoffReceived(SessionChatEvent ev)
 		{	_appendOutput("["+ev.getLobby().getNetworkName()+"]  "+ev.getChatUser().getId()+" has left\n");
			_pushDown();
		}
		public void chatMessageReceived(SessionChatEvent ev)
		{	String u=ev.getChatUser().getId() , m=ev.getMessage();
			int spam=spamBlock.getViolations(u,m);
			if(spam>0)
			{	System.out.println("Blocked: "+u+"/"+spam+": "+m);
			}
			else
			{	if(ev.isEmote())  m="<"+m+">";
				_appendOutput("["+ev.getLobby().getNetworkName()+"]  "+u+" : ");
				decoder.appendToDocument(m,outputDoc);
				_pushDown();
				_log(m);
			}
		}
		public void chatConnectionClosed(SessionEvent ev)
		{	_appendOutput("**Chat connection closed**\n");
			_pushDown();
			System.out.println("**Chat connection closed**");
		}
	}

	// *****************************************************************
	// Current chat lobby / conference / emote
	// *****************************************************************
	class LobbyChooser extends Thread
	{	LobbyChooser() { super(threadGroup,"Lobby Chooser");  this.start(); }
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
				{	// -----Are there any rooms under this category?
					String rm = null;
					int rsz=ycc.getPublicRooms().size();
					if(rsz>0)  rm="Rooms:"+rsz;
					// -----Select
					sel = new Selector(ycc.getCategories(),title,rm);
					r=sel.getSelected();  if(r<0)  break;
					ycc = (YahooChatCategory)(ycc.getCategories().elementAt(r));
					title=title+"->["+ycc.getName()+"]";
				}
				// -----Select a room from category
				sel = new Selector(ycc.getPublicRooms(),"Public rooms",null);
				r=sel.getSelected();
				YahooChatRoom ycr = (YahooChatRoom)(ycc.getPublicRooms().elementAt(r));
				// -----Select a lobby from room
				sel = new Selector(ycr.getLobbies(),ycr.getName()+" lobbies",null);
				r=sel.getSelected();
				YahooChatLobby ycl = (YahooChatLobby)(ycr.getLobbies().elementAt(r));
				// -----Make current lobby
				currentLobby=ycl;  setInputCache(findMode(0x400),0,currentLobby.getNetworkName());
				System.out.println(currentLobby.toString());
			}catch(IOException e) { e.printStackTrace(); }
		}
	}

	class ConfChooser extends Thread
	{	ConfChooser() { super(threadGroup,"Conference Choose");  this.start(); }
		public void run()
		{	if(conferences.size()<=0)  return;
			Selector sel = new Selector(conferences,"Choose a conference",null);
			currentConf = (YahooConference)conferences.elementAt(sel.getSelected());
			int modeOffset=findMode(0x300);
			for(int i=1;i<6;i++)
				setInputCache(modeOffset+i,0,currentConf.getName());
		}
	}

	class EmoteChooser extends Thread
	{	EmoteChooser() { super(threadGroup,"Emote Choose");  this.start(); }
		public void run()
		{	Vector v = emoteManager.getNames();
			if(v.size()<=0)  return;
			Selector sel = new Selector(v,"Choose an Emote",null);
			int idx=sel.getSelected();
			int modeOffset=findMode(0x403);
			setInputCache(modeOffset,1,(String)v.elementAt(idx));
		}
	}

	// *****************************************************************
	// Util class, select params which are not text
	// *****************************************************************
	class Selector extends JFrame implements ActionListener,ListSelectionListener
	{	JList selectorL;
		JButton exitB;
		Vector list;
		int selected;
		String title,buttonText;

		Selector(Vector v,String t,String b)
		{	super("Selector");
			list=v;  title=t;  buttonText=b;  selected=-2;
			initGfx();
		}

		int getSelected()
		{	while(selected<-1)
			{	try { Thread.sleep(100); }catch(InterruptedException e) {}
			}
			setVisible(false);  dispose();
			return selected;
		}

		void initGfx()
		{	selectorL = new JList(list);
			selectorL.addListSelectionListener(this);
			//selectorL.setMultipleMode(false);

			if(buttonText!=null)
			{	exitB = new JButton(buttonText);  exitB.addActionListener(this);
			}

			Container c = getContentPane();
			c.setLayout(new BorderLayout());
			c.add(new JLabel("Select an item from the list: "+title),BorderLayout.NORTH);
			c.add(selectorL,BorderLayout.CENTER);
			if(buttonText!=null)  c.add(exitB,BorderLayout.SOUTH);
			pack();  show();
		}

		public void valueChanged(ListSelectionEvent ev)
		{	selected = selectorL.getSelectedIndex();
		}

		public void actionPerformed(ActionEvent ev)
		{	selected = -1;
		}
	}

	// *****************************************************************
	// Spawm login off into separate thread
	// *****************************************************************
	class LoginThread extends Thread
	{	LoginThread() { super(threadGroup,"Login Thread");  start(); }
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
			System.out.println(session.getConnectionHandler().toString());

			try
			{	JCheckBoxMenuItem invis = (JCheckBoxMenuItem)menuItems.get("INVIS");
				if(invis.getState())
					session.setStatus(StatusConstants.STATUS_INVISIBLE);
			}catch(IOException e) {}

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
	
	class WindowCloser extends JPanel implements ActionListener
	{	JFrame window;
		
		WindowCloser(String title,JFrame f)
		{	window=f;
			JButton exitB = new JButton("Close");  exitB.addActionListener(this);
			setLayout(new BorderLayout(10,10));
			add(new JLabel(title,JLabel.LEFT) , BorderLayout.CENTER);
			add(exitB,BorderLayout.EAST);
		}
		
		public void actionPerformed(ActionEvent ev)
		{	window.dispose();
		}
	}

	// *****************************************************************
	// Display chatroom user list
	// *****************************************************************
	class UserDisplay extends JFrame
	{	JList userL;

		UserDisplay()
		{	super("Users");
			userL = new JList();
			userL.setCellRenderer(new CellRenderer());
			Container con = getContentPane();
			con.setLayout(new BorderLayout());
			con.add(new WindowCloser("Chatroom users",this) , BorderLayout.NORTH);
			con.add(new JScrollPane(userL) , BorderLayout.CENTER);
			pack();  show();
		}

		void setModel(ListModel lm) { userL.setModel(lm); }

		class CellRenderer extends JLabel implements ListCellRenderer
		{	public Component getListCellRendererComponent(JList list,Object value,
				int index,boolean selected,boolean focus)
			{	if(value instanceof YahooUser)
					setText( ((YahooUser)value).getId() );
				else if(value instanceof YahooChatUser)
					setText( ((YahooChatUser)value).getId()+"   "+_attrs((YahooChatUser)value) );
				else
					setText(value.toString());
				setBackground(selected ? Color.lightGray : Color.white);
				return this;
			}
		}
	}
	private String _attrs(YahooChatUser ycu)
	{	String r="";
		if(ycu.isMale())  r=r+"(M";
			else if(ycu.isFemale())  r=r+"(F";
			else r=r+"(?";
		if(ycu.hasWebcam())  r=r+"C";
		r=r+")";
		return r;
	}

	// *****************************************************************
	// Friends groups
	// *****************************************************************
	class GroupDisplay extends JFrame
	{	JTree groupT;

		GroupDisplay()
		{	super("Groups");
			groupT = new JTree();
			groupT.setCellRenderer(new CellRenderer());
			Container con = getContentPane();
			con.setLayout(new BorderLayout());
			con.add(new WindowCloser("Friends",this) , BorderLayout.NORTH);
			con.add(new JScrollPane(groupT) , BorderLayout.CENTER);
			pack();  show();
		}

		void setModel(TreeModel tm) { groupT.setModel(tm); }

		class CellRenderer extends JLabel implements TreeCellRenderer
		{	public Component getTreeCellRendererComponent(JTree tree,Object value,
				boolean selected,boolean expanded,boolean leaf,int row,boolean focus)
			{	if(value instanceof YahooUser)
				{	YahooUser yu = (YahooUser)value;
					setText(yu.getId()+" ("+Long.toHexString(yu.getStatus())+")");
				}
				else if(value instanceof YahooGroup)
				{	setText( ((YahooGroup)value).getName() );
				}
				else
				{	setText(value.toString());
				}
				setBackground(selected ? Color.lightGray : Color.white);
				return this;
			}
		}
	}

	// -----------------------------------------------------------------
	// Debug
	// -----------------------------------------------------------------
	void dump()
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
		String[] cookies = session.getCookies();
		for(int i=0;i<cookies.length;i++)  System.out.println("Cookie "+i+" : "+cookies[i]);
		System.out.println("Id:"+currentIdentity);
	}

	void dumpChat()
	{	if(currentLobby!=null)
		{	Vector v = currentLobby.getMembers();
			for(int i=0;i<v.size();i++)
			{	YahooChatUser ycu = (YahooChatUser)v.elementAt(i);
				System.out.println(ycu.toString());
			}
		}
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
	{	String un=null,ps=null,md=null,tf=null;

		for(int i=0;i<args.length;i++)
		{	if(args[i].startsWith("-u:"))  un=args[i].substring(3);
			if(args[i].startsWith("-p:"))  ps=args[i].substring(3);
			if(args[i].startsWith("-m:"))  md=args[i].substring(3);
			if(args[i].startsWith("-T:"))  tf=args[i].substring(3);
		}

		if(args.length==0)
			System.out.println("Usage: [-u:<username>] [-p:<password>] [-m:direct|socks|http] [-T:<transcript file>]");
		SwingTestClient tc = new SwingTestClient(un,ps,md,tf);
	}
}
