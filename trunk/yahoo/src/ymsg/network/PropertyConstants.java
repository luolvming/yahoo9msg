package ymsg.network;

// *********************************************************************
// Name and default constants for system properties.  These can be
// overridden by using the java.lang.System static methods or the -D
// command line switch.
// *********************************************************************
interface PropertyConstants
{	// -----------------------------------------------------------------
	// Names of properties
	// -----------------------------------------------------------------
	public final static String DIRECT_HOST = "ymsg.network.directHost";
	public final static String DIRECT_PORTS = "ymsg.network.directPorts";
	public final static String HTTP_HOST = "ymsg.network.httpHost";
	public final static String HTTP_PROXY_AUTH = "ymsg.network.httpProxyAuth";
	public final static String FT_HOST = "ymsg.network.fileTransferHost";

	// -----------------------------------------------------------------
	// Property defaults
	// -----------------------------------------------------------------
	// Originally cs12.msg.sc5.yahoo.com:5050, then scs.yahoo.com
	public final static String DIRECT_HOST_DEFAULT = "scs.msg.yahoo.com";
	public final static int[] DIRECT_PORTS_DEFAULT = { 5050,23,25,80 };
	public final static String HTTP_HOST_DEFAULT = "http.pager.yahoo.com";
	public final static String FT_HOST_DEFAULT = "filetransfer.msg.yahoo.com";
}
