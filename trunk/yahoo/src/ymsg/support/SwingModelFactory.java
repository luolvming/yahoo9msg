package ymsg.support;

import java.lang.ref.*;
import java.util.*;
import javax.swing.ListModel;
import javax.swing.event.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import ymsg.network.*;
import ymsg.network.event.*;

// *********************************************************************
// This is a handy class for creating self-updating models. for use with
// Swing's MVC scheme.
//
// This class is rather clever.  It will register itself with the session
// provided, and enacts any events from said session on the model objects
// it creates automatically.  Eg, having created a ListModel for a chat
// room, this class will automatically update the ListModel's status, based
// upon messages received from Yahoo (or rather, the session events they
// generate).
//
// Even more clever is that this class uses weak references to automatically
// drop any models no longer referenced 'outside' the factory - meaning that
// the API user does not explicitly have to tell the factory when a model
// is being discarded - they can just null all references like normal, in the
// knowledge that the factory will detect this and remove its own references!
//
// Lifecycle:
// 1)	The user creates a new model.  The model is remembered by the
// 		factory in a hash.  The key is the Lobby/Conference/whatever object
//		containing the member list being modelled.  The value is a weak
//		reference to the model object itself.  The weak reference is also
//		queued for notification after it is wiped.
// 2)	An Session event occures, and the factory looks the model up in the
//		hash, using the Lobby/Conference/etc object from the event as the
//		key.  If present, the model will be updated.
// 3)	When the user discards the model object, the garbage collector
//		realises that only the weak reference in the factory's hash is
//		holding the model - so it releases the model and adds the weak
//		reference to the queue.
// 4)	Getting the weak reference from the queue, the factory now knows
//		that the model object has been discarded 'outside', and removes
//		the key and weak reference from the hash, thus successfully
//		cleaning up all outstanding objects.
//
// AbstractUserModel is an inner class, which provides useful utility
// code for other models, namely storing and firing listener events.
// The FireEvent inner class spawns a new thread and works through the
// vector of listeners, involking an abstract method on the subclass to
// call the correct listener method, depending upon the mode;
//
// Note: you should not create duplicate models for the same data, as
// subsequent calls will create models which superceed previous models.
// *********************************************************************
public class SwingModelFactory extends SessionAdapter
{	private Session session;					// Session to monitor
	private Hashtable models;					// Current models
	private ReferenceQueue queue;				// Weak ref queue
	private QueueHandler queueHandler;			// Thread to remove weak refs
	private ThreadGroup factoryThreads;			// Thread group

	private static UserComparator userComparator; // Compare user objects
	private static GroupComparator groupComparator; // Compare group objects
	private static DummyListModel dummyListModel; // Empty list
	private static String ROOT = "Groups";		// Represents tree root node

	// -----------------------------------------------------------------
	// CONSTRUCTORS
	// -----------------------------------------------------------------
	public SwingModelFactory(Session ss)
	{	session=ss;  session.addSessionListener(this);
		models = new Hashtable();
		factoryThreads = new ThreadGroup("Swing Model Threads");

		// -----This process takes defunct weak references and removed them
		// -----from the model hashtable
		queue = new ReferenceQueue();
		queueHandler = new QueueHandler();
	}

	static
	{	userComparator = new UserComparator();
		groupComparator = new GroupComparator();
		dummyListModel = new DummyListModel();
	}

	// -----------------------------------------------------------------
	// DESTRUCTOR
	// -----------------------------------------------------------------
	public void finalize() throws Throwable
	{	try
		{	session.removeSessionListener(this);	// Remove session listener
			queueHandler.quit();					// Stop weak ref queue
		}
		catch(Exception e) {}
		finally { super.finalize(); }
	}

	// -----------------------------------------------------------------
	// Session listener methods.  These feed the update of our models.
	// -----------------------------------------------------------------
	public void chatLogonReceived(SessionChatEvent ev)
	{	UserListModel ulm = (UserListModel)getReference(ev.getLobby());
		YahooChatUser[] u = ev.getChatUsers();
		if(ulm!=null)
			for(int i=0;i<u.length;i++)  ulm.addUser(u[i]);
	}
	public void chatLogoffReceived(SessionChatEvent ev)
	{	UserListModel ulm = (UserListModel)getReference(ev.getLobby());
		YahooChatUser u = ev.getChatUser();
		if(ulm!=null)  ulm.removeUser(u);
	}
	public void chatUserUpdateReceived(SessionChatEvent ev)
	{	UserListModel ulm = (UserListModel)getReference(ev.getLobby());
		YahooChatUser u = ev.getChatUser();
		if(ulm!=null)  ulm.updateUser(u);
	}

	public void conferenceInviteReceived(SessionConferenceEvent ev)
	{	UserListModel ulm = (UserListModel)getReference(ev.getRoom());
		YahooUser[] u = ev.getUsers();
		if(ulm!=null)
			for(int i=0;i<u.length;i++)  ulm.addUser(u[i]);
	}
	public void conferenceLogonReceived(SessionConferenceEvent ev)
	{	UserListModel ulm = (UserListModel)getReference(ev.getRoom());
		YahooUser u = ev.getUser();
		if(ulm!=null)  ulm.removeUser(u);
	}
	public void conferenceLogoffReceived(SessionConferenceEvent ev)
	{	UserListModel ulm = (UserListModel)getReference(ev.getRoom());
		YahooUser[] u = ev.getUsers();
		if(ulm!=null)  ulm.removeUser(u);
	}

	public void listReceived(SessionEvent ev)
	{	UserTreeModel utm = (UserTreeModel)getReference(ROOT);
		if(utm!=null)  utm.updateAll();
	}
	public void friendsUpdateReceived(SessionFriendEvent ev)
	{	UserTreeModel utm = (UserTreeModel)getReference(ROOT);
		if(utm!=null)  utm.updateUser(ev.getFriend(),ev.getGroup());
	}
	public void friendAddedReceived(SessionFriendEvent ev)
	{	UserTreeModel utm = (UserTreeModel)getReference(ROOT);
		if(utm!=null)  utm.addUser(ev.getFriend(),ev.getGroup());
	}
	public void friendRemovedReceived(SessionFriendEvent ev)
	{	UserTreeModel utm = (UserTreeModel)getReference(ROOT);
		if(utm!=null)  utm.removeUser(ev.getFriend(),ev.getGroup());
	}


	// -----------------------------------------------------------------
	// Util list methods
	// -----------------------------------------------------------------
	private Object getReference(Object k)
	{	Reference ref=(Reference)models.get(k);
		if(ref!=null)  return ref.get();
			else  return null;
	}

	/*private YahooGroup getGroup(String n)
	{	YahooGroup[] yg = session.getGroups();
		for(int i=0;i<yg.length;i++)
			if(yg[i].getName().equals(n))  return  yg[i];
		return null;
	}*/

	// -----------------------------------------------------------------
	// The hash of current models is needed so that when events arrive
	// from the Session we are listening to, we can dispatch them to
	// all relevant models.
	//
	// Weak references are used because this list of models should not
	// prevent model objects from being garbage collected once they have
	// been discarded outside this factory.  This removes the need for
	// API users to formally tell the factory when they have finished
	// with a model, so it can be removed from the factory's hash, and
	// made eligable to complete GC'ing.
	// -----------------------------------------------------------------
	private void addModel(Object key,Object model)
	{	models.put(key,new WeakReference(model,queue));
	}

	// -----------------------------------------------------------------
	// These methods are used to create the various model classes
	// -----------------------------------------------------------------
	public ListModel createListModel(YahooChatLobby ycl,boolean sort)
	{	ListModel lm = new UserListModel(ycl.getMembers(),sort);
		addModel(ycl,lm);  return lm;
	}

	public ListModel createListModel(YahooConference yc,boolean sort)
	{	ListModel lm = new UserListModel(yc.getMembers(),sort);
		addModel(yc,lm);  return lm;
	}

	public TreeModel createTreeModel(boolean sort)
	{	YahooGroup[] yg = session.getGroups();
		TreeModel tm = new UserTreeModel(yg,sort);
		addModel(ROOT,tm);  return tm;
	}

	// -----------------------------------------------------------------
	// Static method to get an empty list model - handy for blanking list
	// contents.
	// -----------------------------------------------------------------
	public static ListModel getEmptyListModel()
	{	return dummyListModel;
	}

	// *****************************************************************
	// Used as a base class for the other models.  Provides listener
	// utility functionality, common to all models.
	// *****************************************************************
	abstract class AbstractUserModel
	{	Vector listeners;
		boolean sort;

		AbstractUserModel(boolean st) { listeners = new Vector();  sort=st; }
		void addListener(Object o) { if(!listeners.contains(o))  listeners.addElement(o); }
		void removeListener(Object o) { listeners.removeElement(o); }

		abstract void fireEvent(Object listener,EventObject ev,int mode);

		// *************************************************************
		// Threaded event dispatch - defers to concrete method in subclass
		// *************************************************************
		class FireEvent extends Thread
		{	EventObject ev;
			int mode=-1;
			FireEvent(EventObject e,int m)
			{	super(factoryThreads,"Event Fired");
				ev=e;  mode=m;  this.start();
			}
			public void run()
			{	for(int i=0;i<listeners.size();i++)
					fireEvent(listeners.elementAt(i),ev,mode);
			}
		}
	}


	// *****************************************************************
	// Class for ListModel type models
	// *****************************************************************
	class UserListModel extends AbstractUserModel implements ListModel
	{	private Vector members;					// Items in out list

		// -------------------------------------------------------------
		// CONSTRUCTOR
		// -------------------------------------------------------------
		UserListModel(Vector v,boolean st)
		{	super(st);  members=v;
			if(sort)  Collections.sort(members,userComparator);
		}

		// -------------------------------------------------------------
		// Add/remove/update - called by the session event methods
		// -------------------------------------------------------------
		void addUser(Object o)
		{	synchronized(members)
			{	int p=-1;
				members.addElement(o);
				if(sort)
				{	Collections.sort(members,userComparator);
					p=members.indexOf(o);
				}
				else
				{	p=members.size()-1;
				}
				ListDataEvent lde = new ListDataEvent(this,ListDataEvent.INTERVAL_ADDED,p,p);
				new FireEvent(lde,1);
			}
		}
		void removeUser(Object o)
		{	synchronized(members)
			{	int p=members.indexOf(o);
				if(p>-1)
				{	members.removeElementAt(p);
					ListDataEvent lde = new ListDataEvent(this,ListDataEvent.INTERVAL_REMOVED,p,p);
					new FireEvent(lde,2);
				}
			}
		}
		void updateUser(Object o)
		{	synchronized(members)
			{	int p=members.indexOf(o);
				if(p>-1)
				{	ListDataEvent lde = new ListDataEvent(this,ListDataEvent.CONTENTS_CHANGED,p,p);
					new FireEvent(lde,3);
				}
			}
		}

		// -------------------------------------------------------------
		// This is called by the FireEvent inner class of the super class
		// -------------------------------------------------------------
		void fireEvent(Object listener,EventObject ev,int mode)
		{	ListDataListener l = (ListDataListener)listener;
			ListDataEvent lde = (ListDataEvent)ev;
			switch(mode)
			{	case 1 :	l.intervalAdded(lde);  break;
				case 2 :	l.intervalRemoved(lde);  break;
				case 3 :	l.contentsChanged(lde);  break;
				default :	return;
			}
		}

		// -------------------------------------------------------------
		// List model methods
		// -------------------------------------------------------------
		public void addListDataListener(ListDataListener l) { addListener(l); }
		public void removeListDataListener(ListDataListener l) { removeListener(l); }
		public int getSize() { return members.size(); }
		public Object getElementAt(int idx) { return members.elementAt(idx); }
	}

	// *****************************************************************
	// Class for TreeModel type models.
	// *****************************************************************
	class UserTreeModel extends AbstractUserModel implements TreeModel
	{	Hashtable groups;
		Vector groupOrder;

		// -------------------------------------------------------------
		// CONSTRUCTOR
		// -------------------------------------------------------------
		UserTreeModel(YahooGroup[] g,boolean st)
		{	super(st);  groups = new Hashtable();  groupOrder = new Vector();

			// -----Copy the groups, so we can manipulate/sort them
			for(int i=0;i<g.length;i++)
			{	Vector v = g[i].getMembers();
				if(sort)  Collections.sort(v,userComparator);
				groups.put(g[i].getName(),v);  groupOrder.addElement(g[i].getName());
			}
			if(sort)  Collections.sort(groupOrder,groupComparator);
		}

		// -------------------------------------------------------------
		// Add/remove/update - called by the session event methods
		// -------------------------------------------------------------
		void addUser(YahooUser yu,String yg)
		{	Object[] path,childObj;
			int[] childIdx;

			Vector v = (Vector)groups.get(yg);
			if(v==null)
			{	// -----Send event to add new group (with new user)
				v = new Vector();  v.addElement(yu);
				groups.put(yg,v);  groupOrder.addElement(yg);
				Collections.sort(groupOrder,groupComparator);
				path = new Object[1];  path[0]=ROOT;
				childIdx = new int[1];  childIdx[0]=groupOrder.indexOf(yg);
				childObj = new Object[1];  childObj[0]=yg;
			}
			else
			{	// -----Send event to add new user in group
				v.addElement(yu);
				Collections.sort(v,userComparator);
				path = new Object[2];  path[0]=ROOT;  path[1]=yg;
				childIdx = new int[1];  childIdx[0]=v.indexOf(yu);
				childObj = new Object[1];  childObj[0]=yg;
			}
			TreeModelEvent tme = new TreeModelEvent(this,path,childIdx,childObj);
			new FireEvent(tme,1);
		}
		void removeUser(YahooUser yu,String yg)
		{	Object[] path,childObj;
			int[] childIdx;

			Vector v = (Vector)groups.get(yg);
			if(v.size()<=1)
			{	// -----Send event to delete group
				path = new Object[1];  path[0]=ROOT;
				childIdx = new int[1];  childIdx[0]=groupOrder.indexOf(yg);
				childObj = new Object[1];  childObj[0]=yg;
				groups.remove(yg);  groupOrder.removeElement(yg);
			}
			else
			{	// -----Send event to delete user in group
				path = new Object[2];  path[0]=ROOT;  path[1]=yg;
				childIdx = new int[1];  childIdx[0]=v.indexOf(yu);
				childObj = new Object[1];  childObj[0]=yu;
				v.removeElement(yu);
			}
			TreeModelEvent tme = new TreeModelEvent(this,path,childIdx,childObj);
			new FireEvent(tme,2);
		}
		void updateUser(YahooUser yu,String yg)
		{	// -----Send out an event for every occurance of yu in every group
			for(int i=0;i<groupOrder.size();i++)
			{	Vector v=(Vector)groups.get(groupOrder.elementAt(i));
				int idx=v.indexOf(yu);
				if(idx>=0)
				{	Object[] path = new Object[2];  path[0]=ROOT;  path[1]=groupOrder.elementAt(i);
					int[] childIdx = new int[1];  childIdx[0]=idx;
					Object[] childObj = new Object[1];  childObj[0]=yu;
					TreeModelEvent tme = new TreeModelEvent(this,path,childIdx,childObj);
					new FireEvent(tme,3);
				}
			}
		}
		void updateAll()
		{	// -----Send event to update entire tree, from root
			Object[] path = new Object[1];
			path[0]=ROOT;
			TreeModelEvent tme = new TreeModelEvent(this,path);
			new FireEvent(tme,4);
		}

		// -------------------------------------------------------------
		// This is called by the FireEvent inner class of the super class
		// -------------------------------------------------------------
		void fireEvent(Object listener,EventObject ev,int mode)
		{	TreeModelListener l = (TreeModelListener)listener;
			TreeModelEvent tme = (TreeModelEvent)ev;
			switch(mode)
			{	case 1 :	l.treeNodesInserted(tme);  break;
				case 2 :	l.treeNodesRemoved(tme);  break;
				case 3 :	l.treeNodesChanged(tme);  break;
				case 4 :	l.treeStructureChanged(tme);  break;
				default :	return;
			}
		}

		// -------------------------------------------------------------
		// Tree model methods.  Return types are:
		//   'ROOT' (special static String) if root of tree.
		//   'YahooGroup' for children of the root
		//   'YahooUser' for children of a group
		// -------------------------------------------------------------
		public void addTreeModelListener(TreeModelListener l) { addListener(l); }
		public void removeTreeModelListener(TreeModelListener l) { removeListener(l); }
		public Object getChild(Object parent,int index)
		{	if(parent==ROOT)  return  groupOrder.elementAt(index);
			else if(parent instanceof String)  return _get(parent).elementAt(index);
			else  return null;
		}
		public int getChildCount(Object parent)
		{	if(parent==ROOT)  return  groupOrder.size();
			else if(parent instanceof String)  return _get(parent).size();
			else  return -1;
		}
		public int getIndexOfChild(Object parent,Object child)
		{	if(parent==ROOT)  return groupOrder.indexOf(child);
			else if(parent instanceof String)  return _get(parent).indexOf(child);
			else  return -1;
		}
		public Object getRoot() { return ROOT; }
		public boolean isLeaf(Object node) { return (node instanceof YahooUser); }
		public void valueForPathChanged(TreePath path,Object newValue) {}

		// -----Util
		private Vector _get(Object gr) { return (Vector)groups.get(gr); }
	}

	// *****************************************************************
	// Handy empty model
	// *****************************************************************
	static class DummyListModel implements ListModel
	{	public void addListDataListener(ListDataListener l) {}
		public void removeListDataListener(ListDataListener l) {}
		public int getSize() { return 0; }
		public Object getElementAt(int idx) { return null; }
	}


	// *****************************************************************
	// Compares any two YahooUser and YahooChatUser objects.
	// *****************************************************************
	static class UserComparator implements Comparator
	{	public int compare(Object o1,Object o2) throws ClassCastException
		{	String s1,s2;
			// -----Object 1
			if(o1 instanceof YahooUser)  s1=((YahooUser)o1).getId();
			else if(o1 instanceof YahooChatUser)  s1=((YahooChatUser)o1).getId();
			else throw new ClassCastException("Incompatable types");
			// -----Object 2
			if(o2 instanceof YahooUser)  s2=((YahooUser)o2).getId();
			else if(o2 instanceof YahooChatUser)  s2=((YahooChatUser)o2).getId();
			else throw new ClassCastException("Incompatable types");
			// -----Compare
			s1=s1.toLowerCase();  s2=s2.toLowerCase();
			return s1.compareTo(s2);
		}

		public boolean equals(Object o) { return (o==this); }
	}

	// *****************************************************************
	// Compares any two YahooGroup objects.
	// *****************************************************************
	static class GroupComparator implements Comparator
	{	public int compare(Object o1,Object o2) throws ClassCastException
		{	/*YahooGroup g1 = (YahooGroup)o1;
			YahooGroup g2 = (YahooGroup)o2;
			String s1=g1.getName().toLowerCase();
			String s2=g2.getName().toLowerCase();*/
			String s1 = ((String)o1).toLowerCase();
			String s2 = ((String)o2).toLowerCase();
			return s1.compareTo(s2);
		}
	}
	
	// *****************************************************************
	// Cell renderer class for friends list type component.
	// *****************************************************************
	/*class GroupTreeCellRenderer extends JLabel implements TreeCellRenderer
	{	
		public Component getTreeCellRendererComponent(JTree tree,Object value,
			boolean selected,boolean expanded,boolean leaf,int row,boolean focus)
		{	
		}	
	}*/

	// *****************************************************************
	// This thread cleans up references in the models hashtable which
	// are no longer needed.
	// *****************************************************************
	class QueueHandler extends Thread
	{	private boolean quitFlag=false;

		QueueHandler()
		{	super(factoryThreads,"Weak Ref. Queue");
			this.setPriority(Thread.MIN_PRIORITY);  this.start();
		}

		public void run()
		{	try
			{	Reference ref=queue.remove();	// Blocks
				while(!quitFlag && ref!=null)
				{	for(Enumeration e=models.keys();e.hasMoreElements();)
					{	Object k = e.nextElement();
						if(models.get(k)==ref)  models.remove(k);
					}
					ref=queue.remove();			// Blocks
				}
			}catch(InterruptedException e) {}
		}

		public void quit()
		{	quitFlag=true;  this.interrupt();
		}
	}
}
