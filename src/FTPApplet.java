import java.awt.Color;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.PrivilegedActionException;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractButton;
import javax.swing.JApplet;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import netscape.javascript.JSObject;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/**
 * This class implements an applet capable of sending a file or set of files to a specific standard UNIX 
 * FTP server residing on the same host as the page that contains the applet. The send functionality is 
 * controlled completely via JavaScript. The applet also provides a mechanism for using callback functions  
 * to report the percentage of the file sent (in real-time), to provide a status message, and to report
 * file completion (success or failure). These may be set either by parameters to the applet, or via
 * JavaScript calls to the applet.
 * 
 * Authentication (permission to send a file) is based on a security token that is retrieved by the external
 * page prior to the sendFile call. The token is passed into the sendFile call, which then contacts the
 * authentication webservice (again, on the same host as the containing page) and verifies that the 
 * transfer is allowed. If allowed, the webservice gives the applet the username / password for the
 * FTP session, and a directory to place the file in (presumably so that the webservice can process it
 * at a later point). When completed, the applet will send a notification to the webservice with the 
 * filename and the transfer status.
 * 
 * Please note that FTP is not the most secure method of sending a file. Usernames and passwords, as well
 * as the actual data are sent unencrypted, and can be eavesdropped upon by a packet sniffer between the
 * client and the server.
 * 
 * @author Brad Broerman
 * @version 1.5
 */
public class FTPApplet extends JApplet
{
	private static final long serialVersionUID = 6741934940600874652L;
	
	String ftpHost;                   // The host to send the file to. Derived from codebase.
    String ftpTempFolder = "/";       // Folder that we upload to. Set from the authorization service.
    String ftpUserName = null;        // User name for the FTP server. Set by the authorization service.
    String ftpUserPass = null;        // Password for the FTP server. Also set by the authorization service.
    String currAccessCode = null;     // Access code. Set by JavaScript before calling auth service, and read afterwards.
    String lastFolderSelected = null; // The directory the user last selected files from.
    
    ArrayList<String> listOfFilesSelected = null;  // Tracks the files selected last time the UI was launched. Only these files may be sent.
    
    String jsOvrPctCallBackMethod = null;     // Overall percentage complete callback.
    String jsPctCallBackMethod = null;        // Current file percentage complete callback.
    String jsSttCallBackMethod = null;        // Status message callback.
    String jsFileSentCallBackMthd = null;     // Current file sent/complete callback.
    String jsFinalCallBackMthd = null;        // All files sent/complete callback. 
    String jsFileSelectCallBackMethod = null; // Files selected callback.
    
    JSObject window = null;    // JavaScript window object.
    JSObject document = null;  // JavaScript document object.
    
    Boolean returnCodeForTransfer = false; // Used in the FTP transfer ( Work-around for Java bug 6669818 )

    FileSelectThread fileSelectionThread = null; // Thread for file selection. 
    FTPThread ftpTransferThread = null;   // Thread for FTP transfer.
    
    Container content = null;  // Content pane for JFileChooser Swing object.
    
	/**
	 * Called by the web container to set up the Applet instance.
	 * Sets the look and feel, gets references to the web document, and gets the
	 * initial parameters from the applet tag.
	 * 
	 * @access Public
	 * @param None.
	 * @return None.
	 */
    public void init( )
    {
    	//
    	// Set the SWING UI up for the file chooser.
    	//
       	try {
    		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    	} catch(Exception e) {
    		System.out.println("Error setting native 'Look And Feel': " + e);
    	}
    	
        content = getContentPane();
        content.setBackground(Color.white);
        content.setLayout(new FlowLayout());
    	
        //
        //  Dynamically register the JSSE provider (this is in case the base URL for the auth service is HTTPS).
        //
        java.security.Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());

        //
        //  Set this property to use Sun's reference implementation of the HTTPS protocol (if needed).
        //
        System.setProperty("java.protocol.handler.pkgs", "com.sun.net.ssl.internal.www.protocol");

        //
        // The window and document references will be used later for talking to the calling page.
        //
        window = JSObject.getWindow(this);
        document = (JSObject)window.getMember( "document" );

        //
        // Get the host for the FTP. This MUST be the same host that the page is served from.
        //
        ftpHost = getDocumentBase().getHost();

        //
        // Used by the container to make sure the FTPConnection class is available.
        //
        @SuppressWarnings("unused")
		String classArray[] =
        {
            "FTPConnection"
        };

        //
        // Set the JavaScript callback function to report percentage complete (as a parameter).
        //
        jsPctCallBackMethod = this.getParameter("FilePercentCallBack");
        jsOvrPctCallBackMethod = this.getParameter("PercentCallback");

        //
        // Set the callback function for status messages (as a parameter).
        //
        jsSttCallBackMethod = this.getParameter("StatusCallback");

        //
        // Set the callback function for file complete message.
        //
        jsFinalCallBackMthd = this.getParameter("FTPCompleteCallback");
        jsFileSentCallBackMthd = this.getParameter("FileCompleteCallback");
        
        //
        // Set the callback functino for the selected files (from the file chooser)
        //
        jsFileSelectCallBackMethod = this.getParameter("FileSelectCallback");
    }

    /**
     * Set / Reset the JavaScript callback to report overall percentage complete.
     * 
	 * @access Public
     * @param String callBackFuncName - The name of the JavaScript callback function.
     * @return None.
     * @throws None.
     */
    public void setPercentCallBack( String callBackFuncName )
    {
    	jsOvrPctCallBackMethod = callBackFuncName;
    }

    /**
     * Set / Reset the JavaScript callback to report percentage complete for the current file.
     * 
     * @access Public
     * @param String callBackFuncName - The name of the JavaScript callback function.
     * @return None.
     * @throws None.
     */
    public void setFilePercentCallBack( String callBackFuncName )
    {
        jsPctCallBackMethod = callBackFuncName;
    }
    
    /**
     * Set / Reset the callback for status messages.
     * 
	 * @access Public
     * @param String callBackFuncName - The name of the JavaScript callback function.
     * @return None.
     * @throws None.
     */
    public void setStatusCallBack( String callBackFuncName )
    {
        jsSttCallBackMethod = callBackFuncName;
    }

    /**
     * Set the callback function for reporting the send status of the current file.
     * 
	 * @access Public
     * @param String callBackFuncName - The name of the JavaScript callback function.
     * @return None.
     * @throws None.
     */
    public void setFileCompleteCallBack( String callBackFuncName )
    {
    	jsFileSentCallBackMthd = callBackFuncName;
    }
    
    /**
     * Set the callback function for reporting the send status for the full transaction.
     * 
	 * @access Public
     * @param String callBackFuncName - The name of the JavaScript callback function.
     * @return None.
     * @throws None.
     */
    public void setFtpCompleteCallBack( String callBackFuncName )
    {
    	jsFinalCallBackMthd = callBackFuncName;
    }    
    
    /**
     * Set the callback function to pass back the selected files (from the file chooser)
     * 
	 * @access Public
     * @param String callBackFuncName - The name of the JavaScript callback function.
     * @return None.
     * @throws None.
     */   
    public void setFileSelectCallBack( String callBackFuncName )
    {
    	 jsFileSelectCallBackMethod = callBackFuncName;
    }
    
    /**
     * This function is used to read the new session security code after a send has been started.
     * this code will be used in the JavaScript code to validate all future interactions.
     * 
	 * @access Public
     * @param None.
     * @return String - the current access code (if set)
     * @throws None.
     */
    public String getAccessCode( )
    {
        return currAccessCode;
    }
    
    /**
     * Used to set the session security code. 
     * 
	 * @access Public
     * @param String accessCode - The new session security code.
     * @return None.
     * @throws None.
     */ 
    public void setAccessCode( String accessCode )
    {
    	currAccessCode = accessCode;
    }
    
    /**
     * Send one or more files to the server via SFTP. This method is called from JavaScript on the document.
     * 
	 * @access Public
     * @param String accessCode - The access code for the current transaction. 
     * @param String fileList - Semicolon delimited list of files to send. 
     * @return Boolean - True for success, False for error.
     * @throws None. 
     */
    public boolean sendFiles(final String accessCode, final String fileList)
    {
    	returnCodeForTransfer = false;
        
      SwingUtilities.invokeLater(new Runnable() {
    	  public void run() {
    		  returnCodeForTransfer = sendFilesLater(accessCode, fileList);
    	  }
      });
      
      return returnCodeForTransfer;
    }    
    
    /**
     * Send one or more files to the server via SFTP. This method is called from JavaScript on the document.
     * The files are determined directly from the last run file selection. 
     * 
	 * @access Public
     * @param String accessCode - The access code for the current transaction. 
     * @return Boolean - True for success, False for error.
     * @throws None. 
     */
    public boolean sendFiles(final String accessCode)
    {
    	returnCodeForTransfer = false;
        
      SwingUtilities.invokeLater(new Runnable() {
    	  public void run() {
    		  returnCodeForTransfer = sendFilesLater(accessCode, "");
    	  }
      });
      
      return returnCodeForTransfer;
    }  
    
    /**
     * Send one or more files to the server via SFTP. This method is called from JavaScript on the document.
     * The files are determined directly from the last run file selection. 
     * The access code is the internal code from the last applet issued transaction with the webservice.
     * 
	 * @access Public
     * @param None. 
     * @return Boolean - True for success, False for error.
     * @throws None. 
     */
    public boolean sendFiles( )
    {
    	returnCodeForTransfer = false;
        
      SwingUtilities.invokeLater(new Runnable() {
    	  public void run() {
    		  returnCodeForTransfer = sendFilesLater("", "");
    	  }
      });
      
      return returnCodeForTransfer;
    }        
    
    
    /**
     * Abort the currently running FTP file / batch.
     * 
	 * @access Public
     * @param None. 
     * @return None.
     * @throws None. 
     */
    public void abort()
    {
        if( null != ftpTransferThread && false != ftpTransferThread.isAlive())
       	{
        	// This is used, since we can't really interrupt an IO thread. When this
        	// variable is set, it will also set a flag in the FTPConnection that is 
        	// checked inside the main I/O loop.
        	ftpTransferThread.interrupt[0] = true;
       	}    	
    }
    
    /**
     * This is a hack work-around for Java bug 6669818. Sun is not likely to fix this,
     * as they have closed the bug without an official work-around or a fix... merely a
     * confirmation of the bug.
     * 
     * @access Private
     * @param String accessCode - The access code for the current transaction. 
     * @param String fileList - Semicolon delimited list of files to send. 
     * @return Boolean - True for success, False for error.
     * @throws None. 
     */
    public boolean sendFilesLater( String accessCode, String filesToSend )
    {
        boolean returnCode = false;
        
        //
        // Multiple files may be passed in, as a semi-colon separated list
        //
        String[] fileList = new String[1];;
        
        if( null != filesToSend && filesToSend.indexOf(';') > 0)        	
        	fileList = filesToSend.split(";");
        else if( null != filesToSend && filesToSend.length() > 0 ) 
        {
        	fileList[0] = new String(filesToSend);
        }
        else
        {
            fileList = listOfFilesSelected.toArray( fileList );
        }
        
        //
        // If the passed in accessCode is blank or null, use the internal one.
        //
        if( null == accessCode || accessCode.length() == 0  )
        {
        	accessCode = this.currAccessCode;
        }
        else
        {
            this.currAccessCode = accessCode;
        }
        
        //        
        // Filter incoming fileList to those files in listOfFilesSelected.
        //
        fileList = intersection( fileList,
        		                 listOfFilesSelected.toArray(new String[listOfFilesSelected.size()]));
                
        //
        // We can only send one set of files at a time, so make sure the send thread isn't in operation.
        //
        if( null == ftpTransferThread || false == ftpTransferThread.isAlive())
       	{
            //
            // Get authorization for this transfer. This will come from the authorization
            // server by passing the current valid access code. This is compared to the one
            // stored on the server for the current session. If they match, the transfer can
            // proceed. This call also sets the class variables for the FTP user name, password,
            // and temporary directory, as well as the new current code.
            //
            if( getAuthentication(accessCode, filesToSend))
            {
    			// Create our file selection thread
       			ftpTransferThread = new FTPThread( fileList );        		

       			// and let it start running
       			ftpTransferThread.start();

       			// Indicate that we started successfully.
       			returnCode = true;
            }
            else
            {
                //
                //  Authentication with host server failed.
                //
                if( null != jsSttCallBackMethod)
                    window.eval( jsSttCallBackMethod + "('Unable to authorize FTP transfer.');");
                else
                	System.err.println("FTPApplet - Unable to authorize FTP transfer.");
                
				if( null != jsFinalCallBackMthd )
					window.eval( jsFinalCallBackMthd + "( false );");
            }
        }
        return returnCode;
    }

    /**
     * Open the file chooser in a separate thread, and let the users select one or more files.
     * The selected files will be sent back to JavaScript with the registered callback function.
     *
     * @access Public
     * @param boolean rememberLastDir - Start in the directory last selected.
     * @return boolean - True on success, False on error. 
     */
    public boolean getFiles( boolean rememberLastDir )
    {
    	boolean returnCode = false;
    	
    	//
    	// Only one thread at a time.
    	//
       	if( null == fileSelectionThread || false == fileSelectionThread.isAlive())
       	{
    		// Create our file selection thread
       		if( rememberLastDir )
       			fileSelectionThread = new FileSelectThread(true,lastFolderSelected);
       		else
    		    fileSelectionThread = new FileSelectThread(true);

    		// and let it start running
    		fileSelectionThread.start();
    		
    		returnCode = true;
       	}
       	
   		return returnCode;
    }
    
    /**
     * Open the file chooser in a separate thread, and let the users select only ONE file.
     * The selected file will be sent back to JavaScript with the registered callback function.
     *
     * @access Public
     * @param boolean rememberLastDir - Start in the directory last selected.
     * @return boolean - True on success, False on error. 
     */
    public boolean getSingleFile( boolean rememberLastDir )
    {
    	boolean returnCode = false;
    	
    	//
    	// Only one thread at a time.
    	//
       	if( null == fileSelectionThread || false == fileSelectionThread.isAlive())
       	{
    		// Create our file selection thread
       		if( rememberLastDir )
       			fileSelectionThread = new FileSelectThread(false,lastFolderSelected);
       		else
    		    fileSelectionThread = new FileSelectThread(false);

    		// and let it start running
    		fileSelectionThread.start();
    		
    		returnCode = true;
       	}
       	
   		return returnCode;
    }
    
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
	private Document callWebService( String serviceName, String message ) throws PrivilegedActionException
    {
        Document xmlReply = null;
        
        try 
        {
        	final String f_service = serviceName;
        	final String f_parms = message;
            xmlReply = (Document)java.security.AccessController.doPrivileged(new java.security.PrivilegedExceptionAction() 
        	{
                public Object run()  throws ServerException, SAXException, ParserConfigurationException, IOException
                {
            	    Document xmlReply = doCallWebService( f_service, f_parms );
    		        return xmlReply;
    		    } 
             } );            	
        }
        catch ( PrivilegedActionException e )
        {
            throw e;
        }
        
        return xmlReply;
    }
    
    //
    // Call an XML web service. Parameters may be either GET (as part of the service name) or POST as part of the message.
    //
    private Document doCallWebService( String serviceName, String message ) throws MalformedURLException, IOException, ServerException, SAXException, ParserConfigurationException 
    {
    	
    	//
    	// Create the URL object, and open the connection to the web service.
    	//
   	    URL url = new URL( super.getDocumentBase(), serviceName );

   	    //
   	    // We know that it's either HTTP or HTTPS, so we can cast to the
   	    // HttpUrlConnection class here. This way, we can get the response
   	    // code directly.
   	    //
   	    
   	    HttpURLConnection con = (HttpURLConnection)url.openConnection();

        con.setDoInput(true);
        con.setDoOutput(true);
        con.setUseCaches(false);

        //
        // Set the content length header, based on the message size.
        //
        con.setRequestProperty("CONTENT_LENGTH", "" + message.length() ); // actually not checked

        //
        // Copy the session cookies from the parent page, and include them on this request.
        //
        // con.setRequestProperty("Cookie", (String) document.getMember( "cookie" ));

        //
        // Prepare to write to the server.
        //
        OutputStream os = con.getOutputStream();

        //
        // Send the request.
        //
        os.write(message.getBytes("UTF-8"));

        os.flush();
        os.close();

        //
        // Now, read the response!
        //

        //
        // Check the response header. Make sure we're getting a response in the 200 range.
        //        
        if( con.getResponseCode() >= 300 || con.getResponseCode() < 200 )
        {
        	System.err.println(" Got bad response code: (" + Integer.toString(con.getResponseCode()) + ") " + con.getResponseMessage() );
            // 
            // If not, throw an exception to notify the caller that there was a problem.
            //
        	// 
        	throw new ServerException( con.getResponseMessage() );
        }
        
        //
        // Now, get the response text. Convert it to a Document.
        //
      	DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
       	Document doc = docBuilder.parse(con.getInputStream());
       	
        //
        // Return the response to the caller
        //
        return doc;
    } 
    
    private List<ExampleFileFilter> getValidFileTypes( String accessCode, boolean multiFiles )
    {
        //
        // This method will call the file authorization webservice to get the list
    	// of allowable file types. The response will be in the XML format:
    	// <response>
    	//    <success>
    	//      <filter desc='name of filter' default='true'>
    	//        <ext> extension </ext>
    	//        <ext> extension </ext>
    	//        <ext> extension </ext>
    	//      </filter>
    	//      <filter desc='name of filter'>
    	//        <ext> extension </ext>
    	//        <ext> extension </ext>
    	//        <ext> extension </ext>
    	//      </filter>
    	//      <newauth> new code </newauth>
        //   </success>
    	// </response>
    	// etc...
        try
        {
            //
            // Set the security key, and action (upload photo). These will be POST parameters.
            //
        	if( null == accessCode )
        		accessCode = "";
        	
            String params = "key=" + URLEncoder.encode(accessCode, "UTF-8") + "&action=getvalidfileslist";

            if(multiFiles)
            {
            	params += "&multifile=true";
            }
            
            //
            // Call the webservice.
            //
       	    Document xmlReply = callWebService( "ftpauthserver.php", params );

            //
            // Now, if we get other responses (other than the 200 OK) we won't get what we're looking for
            // below anyway, and we'll get an authentication error... That's ok... The full error message
            // will be written to the Java Console log, so we can debug.
            //
            if( xmlReply.getElementsByTagName("response").getLength() > 0 )
            {
            	Element successNode = (Element) xmlReply.getElementsByTagName("success").item(0);
            	if( null != successNode )
            	{
            		List<ExampleFileFilter> returnList = new ArrayList<ExampleFileFilter>();

            		// Now, get the list of filters
            		NodeList filterNodes = successNode.getElementsByTagName("filter");
            		
            		for( int idx = 0; idx < filterNodes.getLength(); ++idx)
            		{
            			ExampleFileFilter newFilter = new ExampleFileFilter();

            			Element currFilter = (Element) filterNodes.item(idx);
            			
            			String desc = currFilter.getAttribute("desc");
            			if( desc == null || desc.length() == 0 )
            			{
            				desc = "";
            			}            			
            			newFilter.setDescription(desc);
            			
            			String def = currFilter.getAttribute("default");
            			if( def != null && def.length() > 0 && def.equalsIgnoreCase("true"))
            			{
            				newFilter.setDefault(true);
            			}            			
            			            			
            			NodeList extensions = currFilter.getElementsByTagName("ext");
                		for( int extidx = 0; extidx < extensions.getLength(); ++extidx)
                		{
                			Element currext = (Element) extensions.item(extidx);                			
                			newFilter.addExtension(currext.getTextContent());	
                		}
                		
                		returnList.add(newFilter);
            		}
            		
            		currAccessCode = successNode.getElementsByTagName("newauth").item(0).getTextContent();

            		return returnList;
                }
            	else
            	{
            		// if no success nodes, then there has to be a failed node.
                	Element failedNode = (Element) xmlReply.getElementsByTagName("failed").item(0);
            		
                	if( null != failedNode )
                		System.err.println("FTPApplet::getValidFileTypes() - Failed getting filter list: " + failedNode.getTextContent());
            	}
            }
            else
            {
                System.err.println("FTPApplet::getValidFileTypes() - Unknown response from action server.");
            }
        }
        catch(Exception e)
        {
            System.err.println("FTPApplet::getValidFileTypes() - Caught Exception: " + e.toString());
            e.printStackTrace();
        }

        return null;
    }

    private boolean getAuthentication( String accessCode, String fileList )
    {
        //
        // This method will call a special method on the bbphotoadminaction.php
        // webservice to determine if the current user is authorized to upload a
        // file to the server. It will pass the session ID and the current
        // code string (used to validate that the connection wasn't hacked). If the
        // webservice determines we can send, it will send back the FTP credentials and
        // a new security code. These will be stored in the appropriate class variables.
        //
    	if( null == accessCode )
    		accessCode = "";

        try
        {
            //
            // Set the security key, and action (upload photo). These will be POST parameters.
            //
            String params = "key="+URLEncoder.encode(accessCode, "UTF-8")+"&action=uploadPhoto" +
                            "&filelist="+URLEncoder.encode(fileList, "UTF-8");

            //
            // Call the webservice.
            //
       	    Document xmlReply = callWebService( "ftpauthserver.php", params );
       	    
            //
            // Now, if we get other responses (other than the 200 OK) we won't get what we're looking for
            // below anyway, and we'll get an authentication error... That's ok... The full error message
            // will be written to the Java Console log, so we can debug.
            //
            if( xmlReply.getElementsByTagName("response").getLength() > 0 )
            {
                //
                // We should get back an XML document with the
                // following parameters:
                //
                //     <response>
                //         <success>
                //             <dir> directory_name </dir>
                //             <usr> ftp_user_name </usr>
                //             <pass> ftp_password </pass>
                //             <newauth> new code </newauth>
                //         </success>
                //     </response>
                //
                //  The directory name, username, and password will bet set in instance variables for
                //  later use.
                //
                // A failure would change the <success> with <failed> and a reason.
                //
            	Element successNode = (Element) xmlReply.getElementsByTagName("success").item(0);

            	if( null != successNode )
            	{
                    //
                    // Now, get each of the required parameters...
                    //
                    ftpTempFolder = successNode.getElementsByTagName("dir").item(0).getTextContent();
                    ftpUserName = successNode.getElementsByTagName("usr").item(0).getTextContent();
                    ftpUserPass = successNode.getElementsByTagName("pass").item(0).getTextContent();
                    currAccessCode = successNode.getElementsByTagName("newauth").item(0).getTextContent();
                    
                    return true;
                }
            	else
            	{
            		// if no success nodes, then there has to be a failed node.
                	Element failedNode = (Element) xmlReply.getElementsByTagName("failed").item(0);
            		
                	if( null != failedNode )
                		System.err.println("Authorization failed: " + failedNode.getTextContent());
            	}
            }
            else
            {
                System.err.println("Unknown response from action server.");
            }
        }
        catch(Exception e)
        {
            System.err.println("FTPApplet::getAuthentication() - Caught Exception: " + e.toString());            
        }

        return false;
    }

    private boolean sendFileComplete(int numFilesSent, String fileList )
    {
        //
        // This method will call a special method on the ftpauthserver.php
        // webservice to tell the application that the file has been successfully
        // sent, and can now be processed by the program.
        //    	
        try
        {
        	String params = "key="+URLEncoder.encode(currAccessCode, "UTF-8")+"&action=photoUploadDone&numFilesSent="+
                           Integer.toString(numFilesSent)+"&filelist="+URLEncoder.encode(fileList, "UTF-8");

            //
            // Call the webservice.
            //
       	    Document xmlReply = callWebService( "ftpauthserver.php", params );

            //
            // We should get back an XML document with the
            // following parameters:
            //
            //     <response>
            //         <success>
            //             <newauth> new code </newauth>
            //         </success>
            //     </response>
            //
            // A failure would change the <success> with <failed> and a reason.
            //
            
            System.err.println( xmlReply.toString() );            
            
            if( xmlReply.getElementsByTagName("response").getLength() > 0 )
            {
              	Element successNode = (Element) xmlReply.getElementsByTagName("success").item(0);

               	if( null != successNode )
               	{
                    currAccessCode = successNode.getElementsByTagName("newauth").item(0).getTextContent();
                        
                    return true;
                }
              	else
              	{
              		// if no success nodes, then there has to be a failed node.
                   	Element failedNode = (Element) xmlReply.getElementsByTagName("failed").item(0);
                		
                   	if( null != failedNode )
                		System.err.println("Server error responding to sent notification.");
               	}
            }
            else
            {
                System.err.println("Unknown response from action server.");
            }
        }
        catch(Exception e)
        {
            System.err.println("FTPApplet::sendFileComplete() - Caught Exception: " + e.toString());
        }

        return false;
    }
    
    //
    // This class is the percentage complete callback. It will track both the 
    // percentage for the current file, as well as the overall percentage.
    //
    // The constructor sets the number of files that are going to be sent, as well as
    // the total file size.
    //
    private class FTPCallBack implements FTPConnection.CallBack
    {
		long currFileSize = 0L;  // Size of the current file only.
		long currentCount = 0L;  // Amount of data sent for the current file.

		long ttlFileSize = 0L;     // Number of bytes for all files.
		long currentTtlCount = 0L; // Total number of bytes sent overall.
		
		public FTPCallBack( int numFiles, long totalFileSize )
		{
			this.ttlFileSize =  totalFileSize;
		}
		
		//
		// This is called from within the FTPConnection class whenever a block of data
		// is sent to the server. We will track the amount for both the current file, and
		// for the overall transfer. Depending on the callbacks registered with the applet,
		// we'll call the Javascript.
		//
		public void update(long count) 
		{
			this.currentCount += count;
			
			this.currentTtlCount += count;
			
			double percentage = (double)this.currentCount * 100.0 /  (double)this.currFileSize; 
			
			if( null != jsPctCallBackMethod )
			{				
				window.eval( jsPctCallBackMethod + "(" + Double.toString(percentage) + ");");
			}

			percentage = (double)this.currentTtlCount * 100.0 /  (double)this.ttlFileSize; 
			
			if( null != jsOvrPctCallBackMethod )
			{				
				window.eval( jsOvrPctCallBackMethod + "(" + Double.toString(percentage) + ");");
			}
		}
		
		//
		// This is called at the beginning of each file with either FTPConnection.Get
		// or FTPConnection.Put as the command, the current file pathnames (both local
		// and remote), and the size of the current file.
		//
		public void init(int command, String src, String dest, long size )
		{
			this.currentCount = 0L;
			this.currFileSize = size;
		}
		
		//
		// This is called at the end of each file, with either a TRUE on success, or FALSE on failed. 
		//
		public void end( boolean success )
		{
			long delta = this.currFileSize - this.currentCount;
			update(delta);
		}
	}
    
    //
    // This is the thread for sending one or more files via FTP to the server.
    //
    public class FTPThread extends Thread
    {
        String[] filesToSend = null;
        ArrayList<String> filesSent = new ArrayList<String>();
        
        public boolean interrupt[] = new boolean[0];
        
        
        // Construct the transfer to send one or more files.
        public FTPThread( String[] inFileList )
        {
        	// yeah, it's a shallow copy, but the contents aren't mutable.        	
        	filesToSend = inFileList.clone();
        }
              
        //
        // This method sends the file in a thread, reporting status and percentage complete if
        // callbacks are set. The currFileInProgress boolean flag indicates whether a transfer
        // is currently in progress or not. It is reset when the method is complete.
        //
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public void run()
        {
        	try
        	{
        		//
        		// Create the FTP connection object.
        		//
        		FTPConnection ftpconnObj = new FTPConnection();
                interrupt = FTPConnection.abortTransfer;
        		
        		
        		//
        		// Get the size of all the files we're going to send.
        		//
        		long ttlFileSize = 0L;
                try 
                {
                	Long returnCode = (Long)java.security.AccessController.doPrivileged(new java.security.PrivilegedExceptionAction() 
                	{
                    public Object run()  throws IOException
                    {
        		
            		long ttlFileSize = 0L;
            		for( int idx = 0; idx < filesToSend.length; ++idx )
                	{
                		File currFile = new File(filesToSend[idx]);
                		
                		ttlFileSize += currFile.length();
                    }
            		
            		return new Long( ttlFileSize);
            		} } );
                	
                	ttlFileSize = returnCode.longValue();	
                }
                catch ( PrivilegedActionException e )
                {
                    throw (IOException) e.getException();
                }
        		
        		//
        		// Set the callback for the percentage complete notification to the
        		// JavaScript (if the callback method name is set).
        		//
        		ftpconnObj.setUpdateCallBack(new FTPCallBack(filesToSend.length, ttlFileSize));

        		if( null != jsSttCallBackMethod)
        			window.eval( jsSttCallBackMethod + "('Connecting.');");

        		//
        		// Connect to the FTP server.
        		//
        		if( ftpconnObj.connect(ftpHost) )
        		{
        			//
        			// Log in. The login user name and password are set by the
        			// authentication server, and stored as class variables.
        			//
        			if( ftpconnObj.login(ftpUserName,ftpUserPass) )
        			{
        				//
        				// Change to the temporary upload directory. This parameter
        				// is also set by the call to the authentication server.
        				//
        				if( null != jsSttCallBackMethod)
        					window.eval( jsSttCallBackMethod + "('Sending.');");

        				if( ftpconnObj.changeDirectory(ftpTempFolder) )
        				{
        					int nbrFilesToSend = filesToSend.length;
        					int nbrErrorFiles = 0;
                        	for( int idx = 0; idx < nbrFilesToSend && !interrupt[0]; ++idx )
                        	{
                        		String currFile =  filesToSend[idx];
                        			
                        		if( null != jsSttCallBackMethod)
                        		{
                        			if( nbrFilesToSend > 1 )
                        				window.eval( jsSttCallBackMethod + "('Sending file" + Integer.toString(idx+1) + ".');");
                        			else
                        				window.eval( jsSttCallBackMethod + "('Sending file.');");
                        		}
                        		
                        		//
                        		// Now send the file.
                        		//
                        		try 
                        		{
                        			if(false == ftpconnObj.uploadFile( currFile ) )
                        			{
                            			String tmpStr = currFile.replaceAll("\\\\","\\\\\\\\");

                        				if( null != jsSttCallBackMethod)
                        					window.eval( jsSttCallBackMethod + "('Error uploading \\\'" + tmpStr + "\\\'.');");
                        				
                        				System.err.println( "Error uploading file'" + tmpStr + "'." );
                        				++nbrErrorFiles;
                        			
                        				if( null != jsFileSentCallBackMthd )
                        				{
                        					window.eval( jsFileSentCallBackMthd + "(false,'" + tmpStr + "');");
                        				}
                        			}
                        			else
                        			{
                            			String tmpStr = currFile.replaceAll("\\\\","\\\\\\\\");

                            			if( null != jsFileSentCallBackMthd )
                        				{
                        					window.eval( jsFileSentCallBackMthd + "(true,'" + tmpStr + "');");
                        				}            
                            			
                            			String finalfilename = null;
                            			if( currFile.lastIndexOf("\\") > -1 )
                            				finalfilename = currFile.substring(currFile.lastIndexOf("\\")+1);
                            			else if( currFile.lastIndexOf("/") > -1 )
                            				finalfilename = currFile.substring(currFile.lastIndexOf("/")+1);
                            				
                        				filesSent.add(finalfilename);
                        			}
                        	
                        		} 
                        		catch( Exception e )
                        		{
                        			String tmpStr = currFile.replaceAll("\\\\","\\\\\\\\");

                        			if( null != jsSttCallBackMethod)
                    					window.eval( jsSttCallBackMethod + "('Error uploading \'" + tmpStr + "\'.');");
                    				
                    				System.err.println( "Error uploading file'" + tmpStr + "'." );
                    				++nbrErrorFiles;
                    			
                    				if( null != jsFileSentCallBackMthd )
                    				{
                    					window.eval( jsFileSentCallBackMthd + "(false,'" + tmpStr + "');");
                    				}                        		
                        		}
                        	}
                        	//
                        	// The FTP completed successfully. Notify the server.
                       		// Note, if this notification does not succeed, log it, but
                       		// don't treat this as a critical error.
                       		//                        	
                        	sendFileComplete(nbrFilesToSend - nbrErrorFiles, FTPApplet.join(filesSent,";"));
                       		
                        	if( 0 == nbrErrorFiles )
                        	{
                        		//
                        		// Update the client with the status.
                        		//
                        		if( null != jsSttCallBackMethod)
                        			window.eval( jsSttCallBackMethod + "('Complete.');");

                        		//
                        		// Inform the JavaScript that we're successfully done.
                        		//
        						if( null != jsFinalCallBackMthd )
        							window.eval( jsFinalCallBackMthd + "( true );");

        					}
        					else
        					{
        						// Error: Unable to upload some files.
        						if( null != jsSttCallBackMethod)
        							window.eval( jsSttCallBackMethod + "('Error uploading one or more files.');");

        						if( null != jsFinalCallBackMthd )
        							window.eval( jsFinalCallBackMthd + "( false );");
        					}
        				}
        				else
        				{
        					// Error: Unable to change directory.
        					if( null != jsSttCallBackMethod)
        						window.eval( jsSttCallBackMethod + "('Error changing to temp directory.');");
    						if( null != jsFinalCallBackMthd )
    							window.eval( jsFinalCallBackMthd + "( false );");
        				}
        			}
        			else
        			{
        				// Error: Unable to login.
        				if( null != jsSttCallBackMethod)
        					window.eval( jsSttCallBackMethod + "('Invalid FTP login.');");
						if( null != jsFinalCallBackMthd )
							window.eval( jsFinalCallBackMthd + "( false );");
        			}
        		}
        		else
        		{
        			// Error: Unable to connect.
        			if( null != jsSttCallBackMethod)
        				window.eval( jsSttCallBackMethod + "('Unable to connect to FTP server.');");
					if( null != jsFinalCallBackMthd )
						window.eval( jsFinalCallBackMthd + "( false );");
        		}

        		//
        		// Log out from the FTP server and close the ports.
        		//
        		ftpconnObj.disconnect();
        	}
        	catch( Exception e)
        	{
        		System.err.println("FTPApplet::sendFile() - Caught Exception: " + e.toString());

        		if( null != jsSttCallBackMethod)
        			window.eval( jsSttCallBackMethod + "('Unknown error sending file.');");
				if( null != jsFinalCallBackMthd )
					window.eval( jsFinalCallBackMthd + "( false );");
        	}
        }
    }
    
    //
    // This is the thread for the JFileChooser. 
    //
    private class FileSelectThread extends Thread
    {
    	boolean showMultiFiles = true;
    	String startingFolder = "";
    	
    	@SuppressWarnings("unused")
		public FileSelectThread()
    	{
    		super();
    	}
    	
    	public FileSelectThread( boolean selectMultipleFiles )
    	{
    		super();    		
    		showMultiFiles = selectMultipleFiles;
    	}
    	
        public FileSelectThread( boolean selectMultipleFiles, String startFolder )
    	{
    		super();    		
    		showMultiFiles = selectMultipleFiles;
    		startingFolder = startFolder;
    	}
        
        @SuppressWarnings({ "unchecked", "rawtypes" })
    	public void run()
    	{
            String fileList = (String)java.security.AccessController.doPrivileged(new java.security.PrivilegedAction() 
            {
            	
                public Object run()
                {       
                	JFileChooser chooser = null;
                	
                	if( null != startingFolder )
                	{
                		chooser = new JFileChooser(new File(startingFolder));
                	}
                	else
                	{
                		chooser = new JFileChooser();
                	}
                	
                    //chooser.setFileView(new ImageFileView());
                	
            	    chooser.setMultiSelectionEnabled(showMultiFiles);
            	    
            	    List<ExampleFileFilter>filters = getValidFileTypes( currAccessCode, showMultiFiles );
            	    if( null != filters ) 
            	    {
            	    	Iterator<ExampleFileFilter> iter = filters.iterator();
            	    	while(iter.hasNext())
            	    	{
            	    		ExampleFileFilter nextFilter = iter.next();
            	    		chooser.addChoosableFileFilter(nextFilter);
            	    	
            	    		if( nextFilter.isDefault() )
            	    		{
            	    			chooser.setFileFilter(nextFilter);
            	    		}            	    		
            	    	}            	    
            	    }
            	    
            	    AbstractButton detailbutton = SwingUtils.getDescendantOfType(AbstractButton.class,
                  	      	chooser, "Icon", UIManager.getIcon("FileChooser.detailsViewIcon"));
                  	      
                    AbstractButton listbutton = SwingUtils.getDescendantOfType(AbstractButton.class,
                    		chooser, "Icon", UIManager.getIcon("FileChooser.listViewIcon"));
                  
                    //listbutton.addActionListener( new ListActionListener(chooser) );
                    //detailbutton.addActionListener( new DetailActionListener(chooser) );            	      
            	    
                	if( null == content )
                		System.err.println("Content Pane is null");
                	
                	int returnVal = chooser.showOpenDialog(content);
                	
                	if(returnVal == JFileChooser.APPROVE_OPTION) {
                		
                		StringBuffer fileList = new StringBuffer();
                		File[] files = chooser.getSelectedFiles();
                		listOfFilesSelected = new ArrayList<String>();
                		for( int i = 0; i < files.length; ++i )
                		{
                			String tmpStr = files[i].getAbsolutePath();
                			listOfFilesSelected.add( new String(tmpStr) );
                			tmpStr = tmpStr.replaceAll("\\\\","\\\\\\\\");
                			fileList.append(tmpStr);
                			fileList.append(";");
                		}
                		
                		if( files.length > 0) 
                		{
                			lastFolderSelected = files[0].getParent();                		                          
                	    }
                       		            		
                		return new String(fileList.toString());
                	}
                	return new String("");            	
                }
                
                final class DetailActionListener implements ActionListener {
                	JFileChooser fc;
                   public DetailActionListener(JFileChooser fc)
                   {
                	   this.fc = fc;
                   }
             	   public void actionPerformed(ActionEvent e) {
                        fc.setFileView(null);

             	   }
                }

                final class ListActionListener implements ActionListener {
                	JFileChooser fc;
                    public ListActionListener(JFileChooser fc)
                    {
                 	   this.fc = fc;
                    }
          	        public void actionPerformed(ActionEvent e) {
                         fc.setFileView(new ImageFileView());
          	        }
                }                                 
            } );
            
            try 
            {
            	if( null != jsFileSelectCallBackMethod)
            		window.eval( jsFileSelectCallBackMethod + "('" + fileList + "');");
            }
            catch( Exception e )
            {
            	System.out.println("Caught Exception: " + e.toString() );
            }    		
    	}    
    }   
    
    // This exception will be thrown when the webserver responds with an error HTTP response code.
    private class ServerException extends Exception
    {
    	/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public ServerException( String message )
    	{
    		super(message);
    	}
    }
    
    @SuppressWarnings("rawtypes")
	public static String join(AbstractCollection s, String delimiter) 
    {
   	    StringBuffer buffer = new StringBuffer();
        Iterator iter = s.iterator();
        if (iter.hasNext()) 
        {
            buffer.append(iter.next());
            while (iter.hasNext()) 
            {
                buffer.append(delimiter);
                buffer.append(iter.next());
            }
        }
        return buffer.toString();
    }
    
    public static String[] intersection(String[] first, String[] second) {  
        // initialize a return set for intersections  
        Set<String> intsIntersect = new HashSet<String>();  
  
        // load first array to a hash  
        HashSet<String> array1ToHash = new HashSet<String>();  
        for (int i = 0; i < first.length; i++) {  
            array1ToHash.add(first[i]);  
        }    
  
        // check second array for matches within the hash  
        for (int i = 0; i < second.length; i++) {  
            if (array1ToHash.contains(second[i])) {  
                // add to the intersect array  
                intsIntersect.add(second[i]);  
            }  
        }  
  
        return intsIntersect.toArray(new String[intsIntersect.size()]);            
    }
    
}
