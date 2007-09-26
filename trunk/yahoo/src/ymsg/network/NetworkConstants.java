package ymsg.network;

// *********************************************************************
// Various constant settings for network code.
// *********************************************************************
public interface NetworkConstants
{	// -----Header constants
	public final static byte PROTOCOL = 0x0a;	// Protocol version 10 (YMSG 10)
	public final static byte[] MAGIC = { 'Y','M','S','G' };
	public final static byte[] VERSION = { PROTOCOL,0x00,0x00,0x00 };
	public final static byte[] VERSION_HTTP = { PROTOCOL,0x00,(byte)0xc8,0x00 };
	public final static int YMSG9_HEADER_SIZE = 20;

	// -----File transfer
	/* Now the property ymsg.network.httpFileTransferHost, accessed via Util.class
	public final static String FILE_TF_HOST = "filetransfer.msg.yahoo.com";
	public final static String FILE_TF_URL = "http://"+FILE_TF_HOST+":80/notifyft";
	*/
	public final static String FILE_TF_PORTPATH = ":80/notifyft";
	public final static String FILE_TF_USER = "FILE_TRANSFER_SYSTEM";

	// -----HTTP
	public final static String USER_AGENT = "Mozilla/4.5 [en] (X11; U; FreeBSD 2.2.8-STABLE i386)";
	public final static String END = "\n";	// Line terminator

	// -----HTTP proxy property names
	public final static String PROXY_HOST_OLD = "proxyHost";
	public final static String PROXY_PORT_OLD = "proxyPort";
	public final static String PROXY_HOST = "http.proxyHost";
	public final static String PROXY_PORT = "http.proxyPort";
	public final static String PROXY_SET = "proxySet";
	public final static String PROXY_NON = "http.nonProxyHosts";

	// -----SOCKS proxy property names
	public final static String SOCKS_HOST = "socksProxyHost";
	public final static String SOCKS_PORT = "socksProxyPort";
	public final static String SOCKS_SET = "socksProxySet";

	// -----Cookies in array (see Session.getCookies())
	public final static int COOKIE_Y = 0;
	public final static int COOKIE_T = 1;
	public final static int COOKIE_C = 2;

	// -----Default timouts (seconds)
	public final static int LOGIN_TIMEOUT = 60;

	// -----Chat server

	// -----Buzz string
	public final static String BUZZ = "<ding>";
}
