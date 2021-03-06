FTPApplet
===========
BSD License. You are free to do what you wish with this code, as long as you keep the copyright headers in place.
If you make bug fixes, I would appreciate it if you pushed them back here, but you don't have to if you don't want to.

This applet plugin.jar (which can be found in the jdk)<br>
<br>
Java applet for use in websites as a file uploader that allows multi-file select, and uses FTP for file transfer.<br>
<br>
The plugin will can be configured to use specific file filters (through a call to a webservice) and to validate
the files before sending is allowed. The webservices will always be on the same server that the applets codebase 
is set to. Additionally, before sending, it will call the webservice to retrieve the FTP username and passowrd 
(thus these never have to be code into the applet). At any of these stages, you can add authentication on the 
webservice side.<br>
<br>
You should use a public FTP server for this as the FTP protocol sends all data, commands, and credentials as clear text.
The PHP side webservice can then copy the files after they are authenticated to a proper directory. If you require
security, I would suggest using the SFTP applet instead.<br>
<br>
The applet is programmable via JavaScript. There are methods available to show the user's file select dialog 
(which is a Swing component), get the list of selected files, and to send the files. Callbacks are configurable 
so that the applet can tell the JavaScript the status of a file transfer (success or fail) and the percentage of 
the file(s) sent.<br>
<br>
The callbacks can be set in the applet tag via the following parameters: <br>
<b>FilePercentCallBack</b> - Called periodically with the percentage transferred for the current file. <br>
<b>PercentCallBack</b> - Called periodically with the overall percentage transferred <br>
<b>StatusCallback</b> - Called periodically with the status of the transfer batch <br>
<b>FTPCompleteCallback</b> - Called when each file is done with a status for that file <br>
<b>FileCompleteCallback</b> - Called when the entire batch is completed with the overall status <br>
<b>FileSelectCallback</b> - Called when the use clicks OK on the file selection dialog, and passed the list of files selected (as a comma delimited list)<br>
<br>
Additionally, the methods exposed are: <br>
<b>setPercentCallBack</b> - Sets the Percent Callback function name. <br>
<b>setFilePercentCallBack</b> - Sets the File Percent Callback function name. <br>
<b>setStatusCallBack</b> - Sets the status Callback function name. <br>
<b>setFileCompleteCallBack</b> - Sets the File Complete Callback function name. <br>
<b>setFtpCompleteCallBack</b> - Sets the FTP Complete Callback function name. <br>
<b>setFileSelectCallBack</b> - Sets the File selected Callback function name.<br>
<br>
<b>getAccessCode</b> - return the current authentication nonce code. <br>
<b>setAccessCode</b> - sets the authentication nonce code.<br>
<b>sendFiles( String accessCode, String fileList )</b> - send a list of files (comma delimted list), and use the specified nonce code. The file list must be a subset of those selected by the user the last time the selection dialog was opened. <br>
<b>sendFiles( String accessCode )</b> - send the last user selected list of files. <br>
<b>sendFiles( )</b> - Send the last user selected list of files, and use the internal auth nonce. <br>
<b>getFiles( boolean rememberLastDir )</b> - Show the use file selection dialog. Optionally, remember the last directory selected. <br>
<b>getSingleFile( boolean rememberLastDir )</b> - Same as getFiles, but allows the user to select only one file.<br>
<br>
The applet makes use of the ftpauthserver.php to authorize files, get the list of file filters,
and to indicate when a file has been transferred.<br>
<br>
=== Building ===

This applet requires plugin.jar (which can be found in the jdk) this should be in your build path.

First, you must complie the code and create a JAR file (called FTPApplet.jar)

Then you must sign the jar file (so that it may access the local computer, and make webservice calls).

Creating a test certificate and signing your jar just involves three simple commands. 
The following shows the commands needed to firstly create your test certificate and add the certificate 
(with an alias of me) to a keystore (named myKeyStore).

keytool -genkey -keystore myKeyStore -alias me keytool -selfcert -keystore myKeyStore -alias me

The above only needs to be done once. You can then use the keystore to sign your jar using the following command:

jarsigner -keystore myKeyStore FTPApplet.jar me

=== Using ===

Use the following applet tag to include on a page:
<pre>
&lt;APPLET code="FTPApplet" id="ftpappl" width="1" height="1" archive="FTPApplet.jar" MAYSCRIPT&gt;
&lt;PARAM name="FilePercentCallBack" value="setFilePercent"&gt;
&lt;PARAM name="PercentCallback" value="setPercent"&gt;
&lt;PARAM name="StatusCallback" value="setStatus"&gt;
&lt;PARAM name="FileCompleteCallback" value="finished"&gt;
&lt;PARAM name="FTPCompleteCallback" value="allfinished"&gt;
&lt;PARAM name="FileSelectCallback" value="setFilesSelected"&gt;
Java Applet Support Required
&lt;/APPLET&gt;
</pre>

If the applet loads successfully, you can make calls to it through JavaScript:

document.ftpappl.getFiles(true);

function setFilesSelected( inFileList ) { <br>
  var returnCode = document.ftpappl.sendFiles(currSecurityKey, inFileList); <br>
  currSecurityKey = document.ftpappl.getAccessCode();  <br>
}
