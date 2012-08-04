<?php
    session_start();

    //
    // Constants used by the uplaod manager...
    //
    define('MCE_FTP_USER', '<your FTP username>');
    define('MCE_FTP_PASSWD', '<your FTP password>');
    define('PHOTO_FTP_DIR', '<full pathname to the FTP directory>');
    define('PHOTO_FTP_ROOT', '<relative path to FTP directory for FTP user>');
    
    $FILE_FILTERS = Array( 'Image Files' => Array( 'png', 'jpg', 'jpeg', 'gif' ) );

    
    
    $ftpRoot = PHOTO_FTP_ROOT;
    
    //
    // Try to prevent session fixation attack.
    //
    if (!isset($_SESSION['someuniquewierdkey']))
    {
        session_regenerate_id();
        $_SESSION['someuniquewierdkey'] = true;
    }        

    header("Content-type: text/xml");
    print("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n");  
    print("<response>\n");

    //
    // This can be used to do a security check. the parameter 'key' is regenerated and resent on each transaction
    // and can be checked against the session, along with other paramters to make sure that the user is authorized
    // and the connection has not been compromised. This won't prevent "man in the middle" attacks, but nothing
    // short of a certificate driven encryption (like SSL) will...
    //
    $origKey = $_POST['key'];
    $newKey = $origKey; 	
    $validated = false;
    if( isSet($origKey) && strlen($origKey) > 0 && $origKey == $_SESSION['bbphotoAdminsecureKey'] &&
        isSet( $_SESSION['userIsLoggedIn'] ) && $_SESSION['userIsLoggedIn'] == 'True' &&
        isSet( $_SESSION['username'] ) && strlen( trim( $_SESSION['username'] ) ) > 0 )
    {
        $validated = true;
    }
 
    //
    // Crate the key for the next iteration...
    //
    if( true == $validated  && isSet($_POST['action']) &&  $_POST['action'] == 'uploadPhoto'  )
    {
        //
        //    We are going to be asking permission to Ftp a picture to the server. 
        // This is used by the Java applet, directly. This can also be used to limit 
        // and validate files. In this case, however, we also get a list of the individual files from the
        // applet, so it's less easy to fake.
        //      
        // $_POST['filelist'];  Perhaps we will want to limit it to valid file
        //
        
        //
        // If so, generate a new secure key, and pass back all of the necessary information.    
        //
                  
        $newKey = generateKey( 10 );
        $_SESSION['bbphotoAdminsecureKey'] = $newKey;	

        print("<success>\n");
        print("<dir>".$ftpRoot."</dir>\n");
        print("<usr>".MCE_FTP_USER."</usr>\n");
        print("<pass>".MCE_FTP_PASSWD."</pass>\n");
        print("<newauth>" . $newKey . "</newauth>\n");
        print("</success>\n");          
    }
    else if( isSet($_POST['action']) &&  $_POST['action'] == 'getvalidfileslist' )
    {
        //
        // If so, generate a new secure key, and pass back all of the necessary information.
        //
        print( "<success>\n" );

        foreach( $FILE_FILTERS as $filter_name => $ext_list )
        {
            print( "<filter desc='" . $filter_name . "'>\n" );

            foreach( $ext_list as $ext )
            {
                print( "<ext>" . $ext . "</ext>\n" );
            }

            print( "</filter>\n" );
        }

        print( "<newauth>" . $newKey . "</newauth>\n" );
        print( "</success>\n" );
    }
    else if( isSet($_POST['action']) &&  $_POST['action'] == 'photoUploadDone' )
    {
        print("<success>\n");
        print("<newauth>" . $newKey . "</newauth>\n");
        print("</success>\n");          
    
    }
    else
    {
        //
        // Else, pass back an error message.
        //
        if(true == $validated )
        {
               print("<failed> Invalid command:".$_POST['action']." </failed>\n");
        }
        else
        {
            print("<failed> Not authorized to perform this action. </failed>\n");
        }
    }
            
    print("</response>\n");                 
?>