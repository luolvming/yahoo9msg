package ymsg.support;

import java.io.*;
import java.net.*;
import java.util.jar.*;

// !!!EXPERIMENTAL!!!
// (Look, but don't trust!)

// *********************************************************************
// This code enables classes to be dynamically loaded and stored for
// future use.  It was mainly created to allow for automatic updates for
// the challenge/response code (which Yahoo repeatedly changes).
//
// Typical usage would be to attempt these three steps:
//	1) Try logging in with standard code.
//	2) Check localUpdateAvailable(), and if true try logging in with
//		newInstance().
//	3) Check remoteUpdateAvailable(), and if true call performUpdate()
//		and then try logging in with newInstance().
//	4) Give up!
//
// To create an update file:
// 	1) Write the code according to the required Java interface.
//	2) Package all necessary classes into a Jar with required name
//	   (for example "ymsgx_cr.jar").
//	3) Add a manifest with the following:
//		- jYMSG-X-Main : the class which implements the interface
//		- jYMSG-X-Version : integer version number
//	4) Create property file using Jar name plus ".properties" (eg.
//		"ymsgx_cr.jar.properties" which contains :
//		- jYMSG-X-Version : integer version number (same a manifest)
//	5) Make both files available on a web server.
//	6) Set the clients "ymsg.dynamic.baseURL" to the base URL of where
//		these two files are.
// *********************************************************************
public class DynamicUpdater
{	private final static String REMOTE_PROP = "ymsg.dynamic.baseURL";
	private final static String CR_FILE = "ymsgx_cr.jar";

	private URL versionURL,remoteURL,localURL;
	private File localFile;


	// -----------------------------------------------------------------
	// CONSTRUCTOR (private, use static)
	// -----------------------------------------------------------------
/*	private DynamicUpdater(String name)
	{	String remotePath = System.getProperty(REMOTE_PROP);
		String localPath = "./";  // FIX
		try
		{	remoteURL = new URL(remotePath,name);
			versionURL = new URL(remotePath,name+".properties");
			localFile = new File(localPath+name);
			localURL = new URL(localFile.toURL());
		}catch(MalformedURLException e) { e.printStackTrace(); }
	}

	public DynamicUpdater createChallengeResponseDynamicUpdater()
	{	return new DynamicUpdater(CR_FILE);
	}



	// -----------------------------------------------------------------
	// Loads an instance of the extension class
	// -----------------------------------------------------------------
	public Object newInstance()
	throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException
	{	// -----Is there a local update?
		if(!localUpdateAvailable())
			throw new ClassNotFoundException("No jYMSG update available");
		// -----Get the name of the class we need to construct
		JarFile jf = new JarFile(localFile);
		Attributes attrs = jf.getManifest().getMainAttributes();
		String className=attrs.getValue("jYMSG-X-Main");
		jf.close();
		// -----Get the class
		URL[] u = new URL[1];  u[0]=localURL;
		Loader l = new Loader(u);
		Class cls = findClass(className);
		return cls.newInstance();
	}

	private class Loader extends URLClassLoader
	{	private Loader(URL[] u) { super(u); }
	}

	// -----------------------------------------------------------------
	// Loads
	// -----------------------------------------------------------------
	public void performUpdate() throws IOException
	{	byte[] buffer = new byte[10*1024];

		File f = new File(localFile);
		FileOutputStream op = new FileOutputStream(f);
		InputStream ip = new remoteURL.openStream();
		int sz=ip.read(buffer);
		while(sz>0)
		{	op.write(buffer,0,sz);
			sz=ip.read(buffer);
		}
		dis.close();  dos.close();
	}

	// -----------------------------------------------------------------
	// Returns true if a local update has been downloaded
	// -----------------------------------------------------------------
	public boolean localUpdateAvailable()
	{	File f = new File(localFile);
		return f.exists();
	}

	// -----------------------------------------------------------------
	// Returns true if there is a remote update available and it is of
	// a higher version number than the current version.
	// -----------------------------------------------------------------
	public boolean remoteUpdateAvailable()
	{	int v1,v2;
		// -----Remote version number
		try
		{	Properties remoteProps = new Properties();
			InputStream in = versionURL.openStream();
			remoteProps.load(in);  in.close();
			v1=Integer.parseInt(
				remoteProps.getProperty("jYMSG-X-Version") );
		}catch(Exception e) { v1=0; }
		// -----Local version number
		try
		{	JarFile jf = new JarFile(localFile);
			Attributes attrs = jf.getManifest().getMainAttributes();
			int v2=Integer.parseInt(
				attrs.getValue("jYMSG-X-Version") );
			jf.close();
		}catch(Exception e) { v2=0; }
		// -----All done?  Return if update is available
		return (v2>v1);
	}
*/
}
