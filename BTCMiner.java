/*!
   BTCMiner -- BTCMiner for ZTEX USB-FPGA Modules
   Copyright (C) 2011 ZTEX GmbH
   http://www.ztex.de

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License version 3 as
   published by the Free Software Foundation.

   This program is distributed in the hope that it will be useful, but
   WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
   General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, see http://www.gnu.org/licenses/.
!*/

/* TODO: 
 * longPolling
 * commands: q,r
 * parallel loading of bitstreams
*/ 
 

import java.io.*;
import java.util.*;
import java.net.*;
import java.security.*;
import java.text.*;
import java.util.zip.*;

import ch.ntb.usb.*;

import ztex.*;

// *****************************************************************************
// ******* ParameterException **************************************************
// *****************************************************************************
// Exception the prints a help message
class ParameterException extends Exception {
    public final static String helpMsg = new String (
		"Parameters:\n"+
		"    -host <string>    Host URL (default: http://127.0.0.1:8332)\n" +
		"    -u <string>       RPC User name\n" + 
		"    -p <string>       RPC Password\n" + 
		"    -b <url> <user name> <password> \n" + 
		"                      URL, user name and password of a backup server. Can be specified multiple times. \n"+
		"    -l	<log file>     Log file \n" +
		"    -lb <log file>    Log of submitted blocks file \n" +
		"    -m s|t|p|c        Set single mode, test mode, programming mode or cluster mode\n"+
		"                      Single mode: runs BTCMiner on a single board (default mode)\n" +
		"                      Test mode: tests a board using some test data\n" +
		"                      Programming mode: programs device with the given firmware\n" +
		"                      Cluster mode: runs BTCMiner on all programmed boards\n" +
		"    -v                Be verbose\n" +
		"    -h                This help\n" +
		"Parameters in single mode, test mode and programming mode\n"+
		"    -d <number>       Device Number, see -i\n" +
		"    -f <ihx file>     Firmware file (required in programming mode)\n" + 
		"    -i                Print bus info\n" +
		"Parameters in cluster mode\n"+
		"    -n <number>       Maximum amount of devices per thread (default: 10)\n"+
		"Parameters in programming mode\n"+
		"    -pt <string>      Program devices of the given type (default: program unconfigured devices)\n" +
		"    -ps <string>      Program devices with the given serial number (default: program unconfigured devices)\n" +
		"    -s                Set serial number\n"
	);
		
    
    public ParameterException (String msg) {
	super( msg + "\n" + helpMsg );
    }
}

/* *****************************************************************************
   ******* ParserException *****************************************************
   ***************************************************************************** */   
class ParserException extends Exception {
    public ParserException(String msg ) {
	super( msg );
    }
}    

/* *****************************************************************************
   ******* FirmwareException ***************************************************
   ***************************************************************************** */   
class FirmwareException extends Exception {
    public FirmwareException(String msg ) {
	super( msg );
    }
}    

// *****************************************************************************
// ******* BTCMinerThread ******************************************************
// *****************************************************************************
class BTCMinerThread extends Thread {
    private Vector<BTCMiner> miners = new Vector<BTCMiner>();
    private String busName;
    private PollLoop pollLoop = null;
    
// ******* constructor *********************************************************
    public BTCMinerThread( String bn ) {
	busName = bn;
    }

// ******* add *****************************************************************
    public void add ( BTCMiner m ) {
	synchronized ( miners ) {
	    miners.add ( m );
	    m.name = busName + ": " + m.name;
	}

	if ( pollLoop==null ) {
	    BTCMiner.printMsg("Starting mining thread for bus " + busName);
	    start();
	}
    }

// ******* size ****************************************************************
    public int size () {
	return miners.size();
    }


// ******* find ****************************************************************
    public BTCMiner find ( int dn ) {
	for (int i=0; i<miners.size(); i++ ) {
	    if ( miners.elementAt(i).ztex().dev().dev().getDevnum() == dn )
		return miners.elementAt(i);
	}
	return null;
    }

// ******* busName *************************************************************
    public String busName () {
	return busName;
    }

// ******* running *************************************************************
    public boolean running () {
	return pollLoop != null;
    }

// ******* run *****************************************************************
    public void run () {
	pollLoop = new PollLoop(miners);
	pollLoop.run();
	pollLoop = null;
    }

// ******* printInfo ************************************************************
    public void printInfo ( ) {
	if ( pollLoop != null )
	    pollLoop.printInfo( busName );
    }
}


// *****************************************************************************
// ******* BTCMinerCluster *****************************************************
// *****************************************************************************
class BTCMinerCluster {
    public static int maxDevicesPerThread = 10;

    private Vector<BTCMinerThread> threads = new Vector<BTCMinerThread>();
    private Vector<BTCMiner> allMiners = new Vector<BTCMiner>();
    
// ******* constructor **************************************************************
    public BTCMinerCluster( boolean verbose ) {
	final long infoInterval = 300000;
    
	scan( verbose );
	
	long nextInfoTime = new Date().getTime() + 60000;
	
	while ( threads.size()>0 ) {

	    try {
		Thread.sleep( 300 );
	    }
	    catch ( InterruptedException e) {
	    }
	    
	    for (int i=0; i<allMiners.size(); i++) 
		allMiners.elementAt(i).print();
		
	    if ( new Date().getTime() > nextInfoTime ) {
		double d = 0.0;
		for ( int i=0; i<allMiners.size(); i++ ) {
		    allMiners.elementAt(i).printInfo( true );
		    d+=allMiners.elementAt(i).submittedHashRate();
		}
		for ( int i=0; i<threads.size(); i++ )
		    threads.elementAt(i).printInfo();
		
		BTCMiner.printMsg("Total submitted hash rate: " + String.format("%.1f",  d ) + "MH/s");
		BTCMiner.printMsg(" -------- ");
		nextInfoTime = new Date().getTime() + infoInterval;
	    }
		
	    for (int i=threads.size()-1; i>=0; i--) {
		BTCMinerThread t = threads.elementAt(i);
		if ( !t.running() ) {
		    BTCMiner.printMsg( "Stopped thread for bus " + t.busName() );
    		    threads.removeElementAt(i);
    		}
	    }
	    
	    try {
		int i=0;
		while ( System.in.available() > 0 ) {
		    int j = System.in.read();
		    if (j>=32) 
			i=j;
		}
		if ( i == 114 )
		    scan( verbose );
	    }
	    catch ( Exception e ) {
	    }

	}
    }
    
// ******* add *****************************************************************
    private void add ( BTCMiner m ) {
	int i=0, j=0;
	String bn = m.ztex().dev().dev().getBus().getDirname() + "-" + j;
	while ( i<threads.size() ) {
	    BTCMinerThread t = threads.elementAt(i);
	    if ( bn.equalsIgnoreCase(threads.elementAt(i).busName()) ) {
		if ( t.size() < maxDevicesPerThread )
		    break;
		j++;
		i=0;
		bn = m.ztex().dev().dev().getBus().getDirname() + "-" + j;
	    }
	    else {
		i++;
	    }
	}

	if ( i >= threads.size() )
	    threads.add( new BTCMinerThread(bn) );
	threads.elementAt(i).add(m);
    }

// ******* find ****************************************************************
    private BTCMiner find ( ZtexDevice1 dev ) {
	int dn = dev.dev().getDevnum();
	String bn = dev.dev().getBus().getDirname();
	for ( int i=threads.size()-1; i>=0; i-- )  {
	    BTCMiner m = threads.elementAt(i).find(dn);
	    if (  m != null && bn.equals(m.ztex().dev().dev().getBus().getDirname()) )
		return m;
	}
	return null;
    }

// ******* scan ****************************************************************
    private void scan ( boolean verbose ) {
	long t = new Date().getTime();

	for (int i=0; i<allMiners.size(); i++ ) 
	    allMiners.elementAt(i).startTimeAdjust = t;
	allMiners.clear();

	BTCMiner.printMsg("\n(Re)Scanning bus ... ");

	PollLoop.scanMode = true;

	ZtexScanBus1 bus = new ZtexScanBus1( ZtexDevice1.ztexVendorId, ZtexDevice1.ztexProductId, false, false, 1,  null, 10, 0, 1, 0 );
	if ( bus.numberOfDevices() <= 0 ) {
	    System.err.println("No devices found. At least one device has to be connected.");
	    System.exit(0);
	} 
	for (int i=0; i<bus.numberOfDevices(); i++ ) {
	    try {
		BTCMiner m = find( bus.device(i) );
		if ( m == null ) {
		    m = new BTCMiner ( bus.device(i), null, verbose );
		    m.clusterMode = true;
		    add( m );
		    BTCMiner.printMsg(m.name + ": added");
		}
		else {
		    BTCMiner.printMsg(m.name + ": already running");
		    m.setFreq(m.freqM);
		}
		
		int j = 0;
		while ( j<allMiners.size() && m.name.compareTo(allMiners.elementAt(j).name)>=0 )
		  j++;
		allMiners.insertElementAt(m, j);
    	    }
	    catch ( Exception e ) {
		BTCMiner.printMsg( "Error: "+e.getLocalizedMessage() );
	    }
	}

	t = new Date().getTime();
	for (int i=0; i<allMiners.size(); i++ ) 
	    allMiners.elementAt(i).startTime+= t-allMiners.elementAt(i).startTimeAdjust;
	
	PollLoop.scanMode = false;
	
	BTCMiner.printMsg("\nSummary: ");
	for (int i=0; i<threads.size(); i++ )
	    BTCMiner.printMsg("  Bus " + threads.elementAt(i).busName() + "\t: " + threads.elementAt(i).size() + " devices");
	BTCMiner.printMsg("  Total  \t: " + allMiners.size() + " devices\n");
	BTCMiner.printMsg("\nDisconnect all devices or press Ctrl-C for exit.\nPress \"r\" Enter for re-scanning.\n");
	
	BTCMiner.connectionEffort = 1.0 + Math.exp( (1.0 - Math.sqrt(Math.min(allMiners.size(),maxDevicesPerThread)*allMiners.size())) / 13.0 );
//	System.out.println( BTCMiner.connectionEffort );

    }

}


// *****************************************************************************
// ******* LogString ***********************************************************
// *****************************************************************************
class LogString {
    public Date time;
    public String msg;
    
    public LogString(String s) {
	time = new Date();
	msg = s;
    }
}


// *****************************************************************************
// ******* PollLoop ************************************************************
// *****************************************************************************
class PollLoop {
    public static boolean scanMode = false;

    private double usbTime = 0.0;
    private double networkTime = 0.0;
    private double timeW = 1e-6;
    private Vector<BTCMiner> v;
    public static final long minQueryInterval = 250;

// ******* constructor *********************************************************
    public PollLoop ( Vector<BTCMiner> pv ) {
	v = pv;
    }
	
// ******* run *****************************************************************
    public void run ( ) {
	int maxIoErrorCount = (int) Math.round( (BTCMiner.rpcCount > 1 ? 2 : 4)*BTCMiner.connectionEffort );
	int ioDisableTime = BTCMiner.rpcCount > 1 ? 60 : 30;
	
	while ( v.size()>0 ) {
	    long t0 = new Date().getTime();

	    if ( ! scanMode ) {
		long tu = 0;
		
		synchronized ( v ) {
		    for ( int i=v.size()-1; i>=0; i-- ) {
			BTCMiner m = v.elementAt(i);
			m.usbTime = 0;

			try { 
			    if ( m.checkUpdate() && m.getWork() ) { // getwork calls getNonces
			        m.dmsg("Got new work");
			        m.sendData();
			    }
			    else {
			        m.getNonces();
			    }
			    m.updateFreq();
			    m.printInfo(false);
			}
			catch ( IOException e ) {
			    m.ioErrorCount[m.rpcNum]++;
			    if ( m.ioErrorCount[m.rpcNum] >= maxIoErrorCount ) {
    			        m.msg("Error: "+e.getLocalizedMessage() +": Disabling URL " + m.rpcurl[m.rpcNum] + " for " + ioDisableTime + "s");
    			        m.disableTime[m.rpcNum] = new Date().getTime() + ioDisableTime*1000;
    			        m.ioErrorCount[m.rpcNum] = 0;
			    }
    			}
			catch ( ParserException e ) {
    			    m.msg("Error: "+e.getLocalizedMessage() +": Disabling URL " + m.rpcurl[m.rpcNum] + " for 60s");
    			    m.disableTime[m.rpcNum] = new Date().getTime() + 60000;
    			}
			catch ( Exception e ) {
    			    m.msg("Error: "+e.getLocalizedMessage()+": Disabling device");
    			    m.fatalError = "Error: "+e.getLocalizedMessage()+": Device disabled since " + BTCMiner.dateFormat.format( new Date() );
    			    v.removeElementAt(i);
			}

    			tu += m.usbTime;
    		    }
		}

		t0 = new Date().getTime() - t0;
		usbTime = usbTime * 0.9998 + tu;
		networkTime = networkTime * 0.9998 + t0 - tu;
		timeW = timeW * 0.9998 + 1;
	    }
	    

	    t0 = minQueryInterval - t0;
	    if ( t0 > 5 ) {
		try {
		    Thread.sleep( t0 );
		}
		catch ( InterruptedException e) {
		}	 
	    }
	}
    }

// ******* printInfo ***********************************************************
    public void printInfo( String name ) {
	int oc = 0;
	double gt=0.0, gtw=0.0, st=0.0, stw=0.0;
	for ( int i=v.size()-1; i>=0; i-- ) {
	    BTCMiner m = v.elementAt(i);
	    oc += m.overflowCount;
	    m.overflowCount = 0;
	    
	    st += m.submitTime;
	    stw += m.submitTimeW;
	    
	    gt += m.getTime;
	    gtw += m.getTimeW;
	}
	    
	BTCMiner.printMsg(name + ": poll loop time: " + Math.round((usbTime+networkTime)/timeW) + "ms (USB: " + Math.round(usbTime/timeW) + "ms network: " + Math.round(networkTime/timeW) + "ms)   getwork time: " 
		+  Math.round(gt/gtw) + "ms  submit time: " +  Math.round(st/stw) + "ms" );
	if ( oc > 0 )
	    BTCMiner.printMsg( name + ": Warning: " + oc + " overflows occured. This is usually caused by a slow network connection." );
    }
}

// *****************************************************************************
// *****************************************************************************
// ******* BTCMiner ************************************************************
// *****************************************************************************
// *****************************************************************************
class BTCMiner {

// *****************************************************************************
// ******* static methods ******************************************************
// *****************************************************************************
    static final int maxRpcCount = 32;
    static String[] rpcurl = new String[maxRpcCount];
    static String[] rpcuser = new String[maxRpcCount];
    static String[] rpcpassw = new String[maxRpcCount];
    static int rpcCount = 1;
    
    static int bcid = -1;

    static String firmwareFile = null;
    static boolean printBus = false;

    public final static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    static PrintStream logFile = null;
    static PrintStream blkLogFile = null;
    
    static double connectionEffort = 2.0;
    
// ******* printMsg *************************************************************
    public static void printMsg ( String msg ) {
	System.out.println( msg );
	if ( logFile != null )
	    logFile.println( dateFormat.format( new Date() ) + ": " + msg );
    }

// ******* encodeBase64 *********************************************************
    public static String encodeBase64(String s) {
        return encodeBase64(s.getBytes());
    }

    public static String encodeBase64(byte[] src) {
	return encodeBase64(src, 0, src.length);
    }

    public static String encodeBase64(byte[] src, int start, int length) {
        final String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
	byte[] encodeData = new byte[64];
        byte[] dst = new byte[(length+2)/3 * 4 + length/72];
        int x = 0;
        int dstIndex = 0;
        int state = 0;
        int old = 0;
        int len = 0;
	int max = length + start;

	for (int i = 0; i<64; i++) {
	    byte c = (byte) charSet.charAt(i);
	    encodeData[i] = c;
	}
	
        for (int srcIndex = start; srcIndex<max; srcIndex++) {
	    x = src[srcIndex];
	    switch (++state) {
	    case 1:
	        dst[dstIndex++] = encodeData[(x>>2) & 0x3f];
		break;
	    case 2:
	        dst[dstIndex++] = encodeData[((old<<4)&0x30) 
	            | ((x>>4)&0xf)];
		break;
	    case 3:
	        dst[dstIndex++] = encodeData[((old<<2)&0x3C) 
	            | ((x>>6)&0x3)];
		dst[dstIndex++] = encodeData[x&0x3F];
		state = 0;
		break;
	    }
	    old = x;
	    if (++len >= 72) {
	    	dst[dstIndex++] = (byte) '\n';
	    	len = 0;
	    }
	}

	switch (state) {
	case 1: dst[dstIndex++] = encodeData[(old<<4) & 0x30];
	   dst[dstIndex++] = (byte) '=';
	   dst[dstIndex++] = (byte) '=';
	   break;
	case 2: dst[dstIndex++] = encodeData[(old<<2) & 0x3c];
	   dst[dstIndex++] = (byte) '=';
	   break;
	}
	return new String(dst);
    }

// ******* hexStrToData ********************************************************
    public static byte[] hexStrToData( String str ) throws NumberFormatException {
	if ( str.length() % 2 != 0 ) 
	    throw new NumberFormatException("Invalid length of string");
	byte[] buf = new byte[str.length() >> 1];
	for ( int i=0; i<buf.length; i++) {
	    buf[i] = (byte) Integer.parseInt( str.substring(i*2,i*2+2), 16);
	}
	return buf;
    }

    public static void hexStrToData( String str, byte[] buf ) throws NumberFormatException {
	if ( str.length()<buf.length*2 ) 
	    throw new NumberFormatException("Invalid length of string");
	for ( int i=0; i<buf.length; i++) {
	    buf[i] = (byte) Integer.parseInt( str.substring(i*2,i*2+2), 16);
	}
    }

// ******* dataToHexStr ********************************************************
    public static String dataToHexStr (byte[] data)  {
	final char hexchars[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
	char[] buf = new char[data.length*2];
	for ( int i=0; i<data.length; i++) {
	    buf[i*2+0] = hexchars[(data[i] & 255) >> 4];
	    buf[i*2+1] = hexchars[(data[i] & 15)];
	}
	return new String(buf);
    }

// ******* dataToInt **********************************************************
    public static int dataToInt (byte[] buf, int offs)  {
	if ( offs + 4 > buf.length )
	    throw new NumberFormatException("Invalid length of data");
	return (buf[offs+0] & 255) | ((buf[offs+1] & 255)<<8) | ((buf[offs+2] & 255)<<16) | ((buf[offs+3] & 255)<<24);
    }

// ******* intToData **********************************************************
    public static byte[] intToData (int n)  {
	byte[] buf = new byte[4];
	buf[0] = (byte) (n & 255);
	buf[1] = (byte) ((n >> 8) & 255);
	buf[2] = (byte) ((n >> 16) & 255);
	buf[3] = (byte) ((n >> 24) & 255);
	return buf;
    }

// ******* intToHexStr ********************************************************
    public static String intToHexStr (int n)  {
	return dataToHexStr( reverse( intToData ( n ) ) );
    }

// ******* reverse ************************************************************
    public static byte[] reverse (byte[] data)  {
	byte[] buf = new byte[data.length];
	for ( int i=0; i<data.length; i++) 
	    buf[data.length-i-1] = data[i];
	return buf;
    }

// ******* jsonParse ***********************************************************
// does not work if parameter name is a part of a parameter value
    public static String jsonParse (String response, String parameter) throws ParserException {
	int lp = parameter.length();
	int i = 0;
	while ( i+lp<response.length() && !parameter.equalsIgnoreCase(response.substring(i,i+lp)) )
	    i++;
	i+=lp;
	if ( i>=response.length() )
	    throw new ParserException( "jsonParse: Parameter `"+parameter+"' not found" );
	while ( i<response.length() && response.charAt(i) != ':' )
	    i++;
	i+=1;
	while ( i<response.length() && (byte)response.charAt(i) <= 32 )
	    i++;
	if ( i>=response.length() )
	    throw new ParserException( "jsonParse: Value expected after `"+parameter+"'" );
	int j=i;
	if ( i<response.length() && response.charAt(i)=='"' ) {
	    i+=1;
	    j=i;
	    while ( j<response.length() && response.charAt(j) != '"' )
		j++;
	    if ( j>=response.length() )
		throw new ParserException( "jsonParse: No closing `\"' found for value of paramter `"+parameter+"'" );
	}
	else { 
	    while ( j<response.length() && response.charAt(j) != ',' && response.charAt(j) != /*{*/'}'  ) 
		j++;
	}
	return response.substring(i,j);
    } 


// ******* checkSnString *******************************************************
// make sure that snString is 10 chars long
    public static String checkSnString ( String snString ) {
    	if ( snString.length()>10 ) {
    	    snString = snString.substring(0,10);
	    System.err.println( "Serial number too long (max. 10 characters), truncated to `" + snString + "'" );
	}
	while ( snString.length()<10 )
	    snString = '0' + snString;
	return snString;
    }



// ******* getType *************************************************************
    private static String getType ( ZtexDevice1 pDev ) {
	byte[] buf = new byte[64];
	try {
	    Ztex1v1 ztex = new Ztex1v1 ( pDev );
    	    ztex.vendorRequest2( 0x82, "Read descriptor", 0, 0, buf, 64 );
    	    if ( buf[0] != 1 && buf[0] != 2 ) 
    		throw new FirmwareException("Invalid BTCMiner descriptor version");

    	    int i = 8;
    	    while ( i<64 && buf[i]!=0 )
    		i++;
    	    if ( i < 9)
    		throw new FirmwareException("Invalid bitstream file name");

    	    return new String(buf, 8, i-8);
    	}
    	catch ( Exception e ) {
	    System.out.println("Warning: "+e.getLocalizedMessage() );
	}
	return null;
    }


// *****************************************************************************
// ******* non-static methods **************************************************
// *****************************************************************************
    private Ztex1v1 ztex = null;
    public int numNonces, offsNonces, freqM, freqMDefault, freqMaxM;
    private double freqM1;
    private String bitFileName = null;
    public String name;
    public String fatalError = null;

    public int ioErrorCount[] = new int[maxRpcCount];
    public long disableTime[] = new long[maxRpcCount];
    
    public int rpcNum = 0;
    private int prevRpcNum = 0;
    
    public boolean verbose = false;
    public boolean clusterMode = false;
    
    public Vector<LogString> logBuf = new Vector<LogString>();

    private byte[] blockBuf = new byte[80];
    private byte[] dataBuf = new byte[128];
    private byte[] midstateBuf = new byte[32];
    private byte[] sendBuf = new byte[44];
    private byte[] hashBuf = new byte[32];

    private boolean isRunning = false;
    
    MessageDigest digest = null;
    
    public int[] lastGoldenNonces = { 0, 0, 0, 0, 0, 0, 0, 0 };
    public int[] goldenNonce, nonce, hash7;
    public int submittedCount = 0,  totalSubmittedCount = 0;
    public long startTime, startTimeAdjust;
    
    public int overflowCount = 0;
    public long usbTime = 0;
    public double getTime = 0.0; 
    public double getTimeW = 1e-6; 
    public double submitTime = 0.0; 
    public double submitTimeW = 1e-6; 
    
    public long maxPollInterval = 20000;
    public long infoInterval = 15000;
    
    public long lastGetWorkTime = 0;
    public long ignoreErrorTime = 0;
    public long lastInfoTime = 0;
        
    public double[] errorCount = new double[256];
    public double[] errorWeight = new double[256];
    public double[] errorRate = new double[256];
    public double[] maxErrorRate = new double[256];
    public final double maxMaxErrorRate = 0.1;
    public final double errorHysteresis = 0.1; // in frequency steps

// ******* BTCMiner ************************************************************
// constructor
    public BTCMiner ( ZtexDevice1 pDev, String firmwareFile, boolean v ) throws UsbException, FirmwareException, NoSuchAlgorithmException {
	
	digest = MessageDigest.getInstance("SHA-256");
	verbose = v;

	ztex  = new Ztex1v1 ( pDev );

        if ( firmwareFile != null ) {
    	    try {
		ztex.uploadFirmware( firmwareFile, false );
    	    }
    	    catch ( Exception e ) {
    		throw new FirmwareException ( e.getLocalizedMessage() );
    	    }
    	}
    	    
        if ( ! ztex.valid() || ztex.dev().productId(0)!=10 || ztex.dev().productId(2)!=1 || ztex.dev().productId(3)!=1 )
    	    throw new FirmwareException("Wrong or no firmware");

	getDescriptor();    	    
	
	goldenNonce = new int[numNonces];
	nonce = new int[numNonces];
	hash7 = new int[numNonces];
	
	name = bitFileName+"-"+ztex.dev().snString();
    	msg( "New device: "+ descriptorInfo() );
    	
//    	long d = Math.round( 2500.0 / (freqM1 * (freqMaxM+1) * numNonces) * 1000.0 );
//    	if ( d < maxPollInterval ) maxPollInterval=d;

    	try {
	    msg("FPGA configuration time: " + ztex.configureFpga( "fpga/"+bitFileName+".bit" , true, 2 ) + " ms");
	    
    	    try {
	        Thread.sleep( 10 );
	    }
	    catch ( InterruptedException e) {
	    }	 
	    
	    freqM = -1;
	    updateFreq();
	    
	    lastInfoTime = new Date().getTime();
	}
	catch ( Exception e ) {
	    throw new FirmwareException ( e.getLocalizedMessage() );
	}
	
	
	for (int i=0; i<255; i++) {
	    errorCount[i] = 0;
	    errorWeight[i] = 0;
	    errorRate[i] = 0;
	    maxErrorRate[i] = 0;
	}
	
	startTime = new Date().getTime();
	startTimeAdjust = startTime;
	
	for (int i=0; i<rpcCount; i++) {
	    disableTime[i] = 0;
	    ioErrorCount[i] = 0;
	}
	
    }

// ******* ztex ****************************************************************
    public Ztex1v1 ztex() {
	return ztex;
    }

// ******* msg *****************************************************************
    void msg(String s) {
	if ( clusterMode ) {
	    synchronized ( logBuf ) {
		logBuf.add( new LogString( s ) );
	    }
	}
	else {
	    printMsg( name + ": " + s );
	}
    }

// ******* dmsg *****************************************************************
    void dmsg(String s) {
	if ( verbose )
	    msg(s);
    }

// ******* print ***************************************************************
    public void print () {
	synchronized ( logBuf ) {
	    for ( int j=0; j<logBuf.size(); j++ ) {
	        LogString ls = logBuf.elementAt(j);
	        System.out.println( name + ": " + ls.msg );
		if ( logFile != null ) {
		    BTCMiner.logFile.println( BTCMiner.dateFormat.format(ls.time) + ": " + name + ": " + ls.msg );
		}
	    }
	    logBuf.clear();
	}
    }

// ******* httpGet *************************************************************
    String httpGet(String request) throws MalformedURLException, IOException {
	HttpURLConnection con = (HttpURLConnection) new URL(rpcurl[rpcNum]).openConnection();
        con.setRequestMethod("POST");
        con.setConnectTimeout((int) Math.round(2000.0*BTCMiner.connectionEffort));
        con.setReadTimeout((int) Math.round(2000.0*BTCMiner.connectionEffort));
        con.setRequestProperty("Authorization", "Basic " + encodeBase64(rpcuser[rpcNum] + ":" + rpcpassw[rpcNum]));
        con.setRequestProperty("Accept-Encoding", "gzip,deflate");
        con.setRequestProperty("Content-Type", "application/json");
	con.setRequestProperty("Cache-Control", "no-cache");
        con.setRequestProperty("User-Agent", "ztexBTCMiner");
        con.setRequestProperty("Content-Length", "" + request.length());
        con.setUseCaches(false);
        con.setDoInput(true);
        con.setDoOutput(true);
        
        // Send request
        OutputStreamWriter wr = new OutputStreamWriter ( con.getOutputStream ());
        wr.write(request);
        wr.flush();
        wr.close();

        // read response header
        String rejectReason = con.getHeaderField("X-Reject-Reason");
        if( rejectReason != null && ! rejectReason.equals("") ) {
            msg("Warning: Rejected block: " + rejectReason);
        }

        // read response	
        InputStream is;
        if ( con.getContentEncoding() == null )
    	    is = con.getInputStream();
    	else if ( con.getContentEncoding().equalsIgnoreCase("gzip") )
    	    is = new GZIPInputStream(con.getInputStream());
    	else if (con.getContentEncoding().equalsIgnoreCase("deflate") )
            is = new InflaterInputStream(con.getInputStream());
        else
    	    throw new IOException( "httpGet: Unknown encoding: " + con.getContentEncoding() );

        byte[] buf = new byte[1024];
        StringBuffer response = new StringBuffer(); 
        int len;
        while ( (len = is.read(buf)) > 0 ) {
            response.append(new String(buf,0,len));
        }
        is.close();
        con.disconnect();
        
        ioErrorCount[rpcNum] = 0;

        return response.toString();
    }

// ******* bitcoinRequest ******************************************************
    public String bitcoinRequest( String request, String params) throws MalformedURLException, IOException {
	bcid += 1;
	return httpGet( "{\"jsonrpc\":\"1.0\",\"id\":" + bcid + ",\"method\":\""+ request + "\",\"params\":["+ (params.equals("") ? "" : ("\""+params+"\"")) + "]}" );
    }

// ******* getWork *************************************************************
    public boolean getWork() throws UsbException, MalformedURLException, IOException, ParserException {

	long t = new Date().getTime();
    
	int i = 0;
	while ( i<rpcCount && (disableTime[i]>t) ) 
	    i++;
	if ( i >= rpcCount )
	    return false;

	rpcNum = i;	
	String response = bitcoinRequest("getwork","" );
	t = new Date().getTime() - t;
	getTime = getTime * 0.99 + t;
	getTimeW = getTimeW * 0.99 + 1;

//	try {
	    while ( getNonces() ) {}
//	}
//	catch ( IOException e )
//	    ioErrorCount[rpcNum]++;
//	}

	hexStrToData(jsonParse(response,"data"), dataBuf);
	hexStrToData(jsonParse(response,"midstate"), midstateBuf);
	initWork();
	lastGetWorkTime = new Date().getTime();
	prevRpcNum = i;
	return true;
    }

// ******* submitWork **********************************************************
    public void submitWork( int n ) throws MalformedURLException, IOException {
	long t = new Date().getTime();

	dataBuf[76  ] = (byte) (n & 255);
	dataBuf[76+1] = (byte) ((n >> 8) & 255);
	dataBuf[76+2] = (byte) ((n >> 16) & 255);
	dataBuf[76+3] = (byte) ((n >> 24) & 255);

	dmsg( "Submitting new nonce " + intToHexStr(n) );
	if ( blkLogFile != null )
	    blkLogFile.println( dateFormat.format( new Date() ) + ": " + name + ": " + dataToHexStr(dataBuf) );
	String response = bitcoinRequest( "getwork", dataToHexStr(dataBuf) );
	String err = null;
	try {
	    err = jsonParse(response,"error");
	}
	catch ( ParserException e ) {
	}
	if ( err!=null && !err.equals("null") && !err.equals("") ) 
	    msg( "Error attempting to submit new nonce: " + err );

	for (int i=lastGoldenNonces.length-1; i>0; i-- )
	    lastGoldenNonces[i]=lastGoldenNonces[i-1];
	lastGoldenNonces[0] = n;

	t = new Date().getTime() - t;
	submitTime = submitTime * 0.99 + t;
	submitTimeW = submitTimeW * 0.99 + 1;
    }

// ******* initWork **********************************************************
    public void initWork (byte[] data, byte[] midstate) {
	if ( data.length != 128 )
	    throw new NumberFormatException("Invalid length of data");
	if ( midstate.length != 32 )
	    throw new NumberFormatException("Invalid length of midstate");
	for (int i=0; i<128; i++)
	    dataBuf[i] = data[i];
	for (int i=0; i<32; i++)
	    midstateBuf[i] = midstate[i];
	initWork();
    }

    public void initWork () {
	// data is Middleendian !!!
	for (int i=0; i<80; i+=4 ) {
	    blockBuf[i  ] = dataBuf[i+3];
	    blockBuf[i+1] = dataBuf[i+2];
	    blockBuf[i+2] = dataBuf[i+1];
	    blockBuf[i+3] = dataBuf[i  ];
	}
    }

// ******* getHash ***********************************************************
    public byte[] getHash(byte[] data) {
        digest.update(blockBuf);
        byte[] first = digest.digest();
        byte[] second = digest.digest(first);
        return second;
    }

// ******* getHash ***********************************************************
    public byte[] getHash(int n) {
	
	blockBuf[76  ] = (byte) ((n >> 24) & 255);
	blockBuf[76+1] = (byte) ((n >> 16) & 255);
	blockBuf[76+2] = (byte) ((n >> 8) & 255);
	blockBuf[76+3] = (byte) (n & 255);

/*        digest.update(blockBuf);
        byte[] first = digest.digest();
        byte[] second = digest.digest(first);
        return second; */
        try {
    	    digest.update(blockBuf);
    	    digest.digest(hashBuf,0,32);
    	    digest.update(hashBuf);
    	    digest.digest(hashBuf,0,32);
    	}
    	catch ( DigestException e ) {
    	    msg( "Error calculating hash value: " + e.getLocalizedMessage() ); // should never occur
    	}
        return hashBuf;
    }

// ******* sendData ***********************************************************
    public void sendData () throws UsbException {
	for ( int i=0; i<12; i++ ) 
	    sendBuf[i] = dataBuf[i+64];
	for ( int i=0; i<32; i++ ) 
	    sendBuf[i+12] = midstateBuf[i];
	    
	long t = new Date().getTime();
        ztex.vendorCommand2( 0x80, "Send hash data", 0, 0, sendBuf, 44 );
        usbTime += new Date().getTime() - t;
        
        ignoreErrorTime = new Date().getTime() + 500; // ignore errors for next 1s
	for ( int i=0; i<numNonces; i++ ) 
	    nonce[i] = 0;
        isRunning = true;
    }

// ******* setFreq *************************************************************
    public void setFreq (int m) throws UsbException {
	if ( m > freqMaxM ) m = freqMaxM;

	long t = new Date().getTime();
        ztex.vendorCommand( 0x83, "Send hash data", m, 0 );
        usbTime += new Date().getTime() - t;

        ignoreErrorTime = new Date().getTime() + 2000; // ignore errors for next 2s
    }

// ******* updateFreq **********************************************************
    public void updateFreq() throws UsbException {
	int maxM = 0;
	while ( maxM<freqMDefault && maxErrorRate[maxM+1]<maxMaxErrorRate )
	    maxM++;
	while ( maxM<freqMaxM && errorWeight[maxM]>150 && maxErrorRate[maxM+1]<maxMaxErrorRate )
	    maxM++;

	int bestM=0;
	double bestR=0;
	for ( int i=0; i<=maxM; i++ )  {
	    double r = (i + 1 + ( i == freqM ? errorHysteresis : 0))*(1-maxErrorRate[i]);
	    if ( r > bestR ) {
		bestM = i;
		bestR = r;
	    }
	}
	
	if ( bestM != freqM ) {
	    freqM = bestM;
	    msg ( "Set frequency to " + String.format("%.2f",(freqM+1)*(freqM1)) +"MHz" );
	    setFreq( freqM );
	}
    }

// ******* getNonces ***********************************************************
    public boolean getNonces() throws UsbException, MalformedURLException, IOException {
	if ( !isRunning || disableTime[prevRpcNum] > new Date().getTime() ) return false;
	
	rpcNum = prevRpcNum;
	
	getNoncesInt();
	
        if ( ignoreErrorTime < new Date().getTime() ) {
	    errorCount[freqM] *= 0.995;
    	    errorWeight[freqM] = errorWeight[freqM]*0.995 + 1.0;
            for ( int i=0; i<numNonces; i++ ) {
        	if ( ! checkNonce( nonce[i], hash7[i] ) )
    		    errorCount[freqM] +=1.0/numNonces;
    	    }
    	    
	    errorRate[freqM] = errorCount[freqM] / errorWeight[freqM] * Math.min(1.0, errorWeight[freqM]*0.01) ;
    	    if ( errorRate[freqM] > maxErrorRate[freqM] )
    	        maxErrorRate[freqM] = errorRate[freqM];
    	}

	boolean submitted = false;
        for ( int i=0; i<numNonces; i++ ) {
    	    int n = goldenNonce[i];
    	    if ( n != -offsNonces ) {
    		getHash(n);
    		if ( hashBuf[31]==0 && hashBuf[30]==0 && hashBuf[29]==0 && hashBuf[28]==0 ) {
    		    int j=0;
    		    while ( j<lastGoldenNonces.length && lastGoldenNonces[j]!=n )
    			j++;
        	    if  (j>=lastGoldenNonces.length) {
        	        submitWork( n );
        		submittedCount+=1;
        		totalSubmittedCount+=1;
        	        submitted = true;
        	    }
    		}
    	    }
        }
        return submitted;
    } 

// ******* getNoncesInt ********************************************************
    public void getNoncesInt() throws UsbException {
	byte[] buf = new byte[numNonces*12];
	boolean overflow = false;

	long t = new Date().getTime();
        ztex.vendorRequest2( 0x81, "Read hash data", 0, 0, buf, numNonces*12 );
        usbTime += new Date().getTime() - t;
        
//	System.out.print(dataToHexStr(buf)+"            ");
        for ( int i=0; i<numNonces; i++ ) {
	    goldenNonce[i] = dataToInt(buf,i*12+0) - offsNonces;
	    int j = dataToInt(buf,i*12+4) - offsNonces;
	    overflow |= ((j >> 4) & 0xfffffff) < ((nonce[i]>>4) & 0xfffffff);
	    nonce[i] = j;
	    hash7[i] = dataToInt(buf,i*12+8);
	}
	if ( overflow && ! PollLoop.scanMode )
	    overflowCount += 1;
    }

// ******* checkNonce *******************************************************
    public boolean checkNonce( int n, int h ) throws UsbException {
	int offs[] = { 0, 1, -1, 2, -2 };
//	int offs[] = { 0 };
	for (int i=0; i<offs.length; i++ ) {
	    getHash(n + offs[i]);
	    if ( ( (hashBuf[31] & 255) | ((hashBuf[30] & 255)<<8) | ((hashBuf[29] & 255)<<16) | ((hashBuf[28] & 255)<<24) ) == h + 0x5be0cd19 )
		return true;
    	}
        return false;
    }

// ******* submittedHashRate ***************************************************
    public double submittedHashRate () {
	return fatalError == null ? 4.294967296e6 * totalSubmittedCount / (new Date().getTime()-startTime) : 0;
    }
    
// ******* printInfo ***********************************************************
    public void printInfo( boolean force ) {
	long t = new Date().getTime();
	if ( !force && (clusterMode || lastInfoTime+infoInterval > t || !isRunning) )
	    return;
	    
	if ( fatalError != null ) {
	    printMsg(name + ": " + fatalError);
	    return;
	}

	StringBuffer sb = new StringBuffer( "f=" + String.format("%.2f",(freqM+1)*freqM1)+"MHz" );

	if ( errorWeight[freqM]>20 )
	    sb.append(",  errorRate="+ String.format("%.2f",errorRate[freqM]*100)+"%");

	if ( errorWeight[freqM]>100.1 || maxErrorRate[freqM]>0.001 )
	    sb.append(",  maxErrorRate="+ String.format("%.2f",maxErrorRate[freqM]*100)+"%");

	if ( freqM<255 && (errorWeight[freqM+1]>100.1 || maxErrorRate[freqM+1]>0.001 ) )
	    sb.append(",  nextMaxErrorRate="+ String.format("%.2f",maxErrorRate[freqM+1]*100)+"%");
	    
	sb.append(",  submitted " +submittedCount+" new nonces,  submitted hash rate " + String.format("%.1f",  submittedHashRate() ) + "MH/s");
	submittedCount = 0;
	
	printMsg(name + ": " + sb.toString());
	    
	lastInfoTime = t;
    }

// ******* getDescriptor *******************************************************
    private void getDescriptor () throws UsbException, FirmwareException {
	byte[] buf = new byte[64];
        ztex.vendorRequest2( 0x82, "Read descriptor", 0, 0, buf, 64 );
        if ( buf[0] != 2 ) {
    	    throw new FirmwareException("Invalid BTCMiner descriptor version");
        }
        numNonces = (buf[1] & 255) + 1;
        offsNonces = ((buf[2] & 255) | ((buf[3] & 255) << 8)) - 10000;
        freqM1 = ( (buf[4] & 255) | ((buf[5] & 255) << 8) ) * 0.01;
        freqM = (buf[6] & 255);
        freqMaxM = (buf[7] & 255);
        if ( freqM > freqMaxM )
    	    freqM = freqMaxM;
        freqMDefault = freqM;
        
        int i = 8;
        while ( i<64 && buf[i]!=0 )
    	    i++;
    	if ( i < 9)
    	    throw new FirmwareException("Invalid bitstream file name");
    	bitFileName = new String(buf, 8, i-8);
    }
    
// ******* checkUpdate **********************************************************
    public boolean checkUpdate() {
	long t = new Date().getTime();
	if ( ignoreErrorTime > t ) return false;
	if ( disableTime[prevRpcNum] > t ) return true;
	if ( lastGetWorkTime + maxPollInterval < t ) return true;
	for ( int i=0; i<numNonces ; i++ )
	    if ( nonce[i]<0 ) return true;
	return false;
    }

// ******* descriptorInfo ******************************************************
    public String descriptorInfo () {
	return "bitfile=" + bitFileName + "   f_default=" + String.format("%.2f",freqM1 * (freqMDefault+1)) + "MHz  f_max=" + String.format("%.2f",freqM1 * (freqMaxM+1))+ "MHz";
    }
    

// *****************************************************************************
// ******* main ****************************************************************
// *****************************************************************************
    public static void main (String args[]) {
    
	int devNum = -1;
	boolean workarounds = false;
	
        String firmwareFile = null, snString = null;
        boolean printBus = false;
        boolean verbose = false;

        String filterType = null, filterSN = null;
        
        char mode = 's';
        
        rpcCount = 1; 
        rpcurl[0] = "http://127.0.0.1:8332";
        rpcuser[0] = null;
        rpcpassw[0] = null;

	try {
// init USB stuff
	    LibusbJava.usb_init();

	    
// scan the command line arguments
    	    for (int i=0; i<args.length; i++ ) {
	        if ( args[i].equals("-d") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
    			devNum = Integer.parseInt( args[i] );
		    } 
		    catch (Exception e) {
		        throw new ParameterException("Device number expected after -d");
		    }
		}
		else if ( args[i].equals("-l") ) {
		    i++;
		    if (i>=args.length) {
			throw new ParameterException("Error: File name expected after `-l'");
		    }
		    try {
			logFile = new PrintStream ( new FileOutputStream ( args[i], true ), true );
		    } 
		    catch (Exception e) {
			throw new ParameterException("Error: File name expected after `-l': "+e.getLocalizedMessage() );
		    }
		}
		else if ( args[i].equals("-bl") ) {
		    i++;
		    if (i>=args.length) {
			throw new ParameterException("Error: File name expected after `-dl'");
		    }
		    try {
			blkLogFile = new PrintStream ( new FileOutputStream ( args[i], true ), true );
		    } 
		    catch (Exception e) {
			throw new ParameterException("Error: File name expected after `-bl': "+e.getLocalizedMessage() );
		    }
		}
	        else if ( args[i].equals("-host") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
    			rpcurl[0] = args[i];
		    } 
		    catch (Exception e) {
		        throw new ParameterException("URL expected after -host");
		    }
		}
	        else if ( args[i].equals("-u") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
    			rpcuser[0] = args[i];
		    } 
		    catch (Exception e) {
		        throw new ParameterException("User expected after -u");
		    }
		}
	        else if ( args[i].equals("-p") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
    			rpcpassw[0] = args[i];
		    } 
		    catch (Exception e) {
		        throw new ParameterException("Password expected after -p");
		    }
		}
	        else if ( args[i].equals("-b") ) {
	    	    i+=3;
		    try {
			if (i>=args.length) throw new Exception();
			if ( rpcCount >= maxRpcCount )
			    throw new IndexOutOfBoundsException("Maximum aoumount of backup servers reached");
    			rpcurl[rpcCount] = args[i-2];
    			rpcuser[rpcCount] = args[i-1];
    			rpcpassw[rpcCount] = args[i];
			rpcCount+=1;
		    } 
		    catch (Exception e) {
		        throw new ParameterException("<URL> <user name> <password> expected after -b");
		    }
		}
	        else if ( args[i].equals("-f") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
    			firmwareFile = args[i];
		    } 
		    catch (Exception e) {
		        throw new ParameterException("ihx file name expected afe -f");
		    }
		}
	        else if ( args[i].equals("-pt") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
    			filterType = args[i];
		    } 
		    catch (Exception e) {
		        throw new ParameterException("<string> after -pt");
		    }
		}
	        else if ( args[i].equals("-ps") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
    			filterSN = checkSnString(args[i]);
		    } 
		    catch (Exception e) {
		        throw new ParameterException("<string> after -ps");
		    }
		}
	        else if ( args[i].equals("-m") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
			if ( args[i].length() < 1 ) throw new Exception();
			mode = Character.toLowerCase( args[i].charAt(0) );
			if ( mode != 's' && mode != 't'  && mode != 'p' && mode != 'c' ) throw new Exception();
		    } 
		    catch (Exception e) {
		        throw new ParameterException("s|t|p|c expected afe -m");
		    }
		}
		else if ( args[i].equals("-s") ) {
		    i++;
    	    	    if ( i >= args.length ) {
			throw new ParameterException("Error: String expected after -s");
		    }
    		    snString = checkSnString(args[i]);
		}
		else if ( args[i].equals("-i") ) {
		    printBus = true;
		} 
		else if ( args[i].equals("-v") ) {
		    verbose = true;
		} 
		else if ( args[i].equals("-h") ) {
		        System.err.println(ParameterException.helpMsg);
	    	        System.exit(0);
		}
	        else if ( args[i].equals("-n") ) {
	    	    i++;
		    try {
			if (i>=args.length) throw new Exception();
    			 BTCMinerCluster.maxDevicesPerThread = Integer.parseInt( args[i] );
		    } 
		    catch (Exception e) {
		        throw new ParameterException("Number expected after -n");
		    }
		}
		else throw new ParameterException("Invalid Parameter: "+args[i]);
	    }
	    
	    if ( BTCMinerCluster.maxDevicesPerThread < 1 )
		BTCMinerCluster.maxDevicesPerThread = 127;
	    
	    if ( mode != 't' && mode != 'p' ) {
		if ( rpcuser[0] == null ) {
		    System.out.print("Enter RPC user name: ");
		    rpcuser[0] = new BufferedReader(new InputStreamReader( System.in) ).readLine();
		}

		if ( rpcpassw[0] == null ) {
		    System.out.print("Enter RPC password: ");
		    rpcpassw[0] = new BufferedReader(new InputStreamReader(System.in) ).readLine();
		}
	    }
		
/*	    Authenticator.setDefault(new Authenticator() {
    		protected PasswordAuthentication getPasswordAuthentication() {
        	    return new PasswordAuthentication (BTCMiner.rpcuser, BTCMiner.rpcpassw.toCharArray());
    		}
	    }); */
	    

	    if ( mode == 's' || mode == 't' ) {
		if ( devNum < 0 )
		    devNum = 0;
		ZtexScanBus1 bus = new ZtexScanBus1( ZtexDevice1.ztexVendorId, ZtexDevice1.ztexProductId, true, false, 1,  null, 10, 0, 1, 0 );
		if ( bus.numberOfDevices() <= 0) {
		    System.err.println("No devices found");
		    System.exit(0);
		} 
		if ( printBus ) {
	    	    bus.printBus(System.out);
	    	    System.exit(0);
		}
		
	        BTCMiner miner = new BTCMiner ( bus.device(devNum), firmwareFile, verbose );
		if ( mode == 't' ) { // single mode
		    miner.initWork( 
			hexStrToData( "0000000122f3e795bb7a55b2b4a580e0dbba9f2a5aedbfc566632984000008de00000000e951667fbba0cfae7719ab2fb4ab8d291a20d387782f4610297f5899cc58b7d64e4056801a08e1e500000000000000800000000000000000000000000000000000000000000000000000000000000000000000000000000080020000" ),
			hexStrToData( "28b81bd40a0e1b75d18362cb9a2faa61669d42913f26194f776c349e97559190" )
		    );

		    miner.sendData ( );
		    for (int i=0; i<200; i++ ) {
			try {
			    Thread.sleep( 250 );
			}
			catch ( InterruptedException e) {
			}	 
			miner.getNoncesInt();

    			miner.getHash(miner.nonce[0]);
    			for ( int j=0; j<miner.numNonces; j++ ) {
//    			    byte h7[] = { miner.hashBuf[28], miner.hashBuf[29], miner.hashBuf[30], miner.hashBuf[31] };
//	    		    System.out.println( i +"-" + j + ":  " + intToHexStr(miner.nonce[j]) + "    " + miner.checkNonce(miner.nonce[j],miner.hash7[j]) + "   " + dataToHexStr( h7 ) + "  " + intToHexStr(miner.hash7[j]+0x5be0cd19) + "    " + intToHexStr(miner.goldenNonce[j]) + "      "  + dataToHexStr( miner.getHash( miner.goldenNonce[j]) ) + "     " + miner.overflowCount);
	    		    System.out.println( i +"-" + j + ":  " + intToHexStr(miner.nonce[j]) + "    " + miner.checkNonce(miner.nonce[j],miner.hash7[j]) + "    " + intToHexStr(miner.goldenNonce[j]) + "      "  + dataToHexStr( miner.getHash( miner.goldenNonce[j]) ) + "     " + miner.overflowCount);
	    		}
		    } 
		}
		else { // single mode
		    System.out.println("\nDisconnect device or press Ctrl-C for exit\n");
		    Vector<BTCMiner> v = new Vector<BTCMiner>();
		    v.add ( miner );
		    new PollLoop(v).run(); 
		}
	    }
	    else if ( mode == 'p' ) {
		ZtexScanBus1 bus = ( filterType == null && filterSN == null ) 
			? new ZtexScanBus1( ZtexDevice1.cypressVendorId, ZtexDevice1.cypressProductId, true, false, 1)
			: new ZtexScanBus1( ZtexDevice1.ztexVendorId, ZtexDevice1.ztexProductId, false, false, 1,  null, 10, 0, 1, 0 );
		if ( bus.numberOfDevices() <= 0) {
		    System.err.println("No devices found");
		    System.exit(0);
		} 
		if ( printBus ) {
	    	    bus.printBus(System.out);
	    	    System.exit(0);
		}
		if ( firmwareFile == null )
		    throw new Exception("Parameter -f required in programming mode");

		int imin=0, imax=bus.numberOfDevices()-1;
		if ( devNum >= 0 ) {
		    imin=devNum;
		    imax=devNum;
		}
		
	        ZtexIhxFile1 ihxFile = new ZtexIhxFile1( firmwareFile );
		    
		int j = 0;
		for (int i=imin; i<=imax; i++ ) {
		    ZtexDevice1 dev = bus.device(i);
		    if ( ( filterSN == null || filterSN.equals(dev.snString()) ) &&
			 ( filterType == null || filterType.equals(getType(dev)) ) ) {
			Ztex1v1 ztex = new Ztex1v1 ( dev );
			if ( snString != null ) 
			    ihxFile.setSnString( snString );
			else if ( ztex.valid() )
			    ihxFile.setSnString( dev.snString() );
			System.out.println("\nold: "+ztex.toString());
	    		System.out.println("Firmware upload time: " + ztex.uploadFirmware( ihxFile, false ) + " ms");
			System.out.println("EEPROM programming time: " + ztex.eepromUpload( ihxFile, false ) + " ms");
			System.out.println("new: " + ztex.toString());
		    j+=1;
		    }
		}
		System.out.println("\ntotal amount of (re-)programmed devices: " + j);
	    }
	    else if ( mode == 'c' ) {
		new BTCMinerCluster( verbose );
	    }
	    
	    
	}
	catch (Exception e) {
	    System.out.println("Error: "+e.getLocalizedMessage() );
	} 
   } 

}
