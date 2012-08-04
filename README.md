FTPApplet
=========

Java Applet for use as a file upload in web sites that uses FTP, and allows multi-file select

First, you must complie the code and create a JAR file (called FTPApplet.jar).

Then you must sign the jar file (so that it may access the local computer, and make webservice calls).

Creating a test certificate and signing your jar just involves three simple commands. 
The following shows the commands needed to firstly create your test certificate and add 
the certificate (with an alias of me) to a keystore (named myKeyStore).

keytool -genkey -keystore myKeyStore -alias me
keytool -selfcert -keystore myKeyStore -alias me

The above only needs to be done once.
You can then use the keystore to sign your jar using the following command:

jarsigner -keystore myKeyStore FTPApplet.jar me

