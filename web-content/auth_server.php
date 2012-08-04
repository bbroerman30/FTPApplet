<?php
    session_start();

    //
    // Try to prevent session fixation attack.
    //
    if (!isset($_SESSION['bbphotoinitiated']))
    {
        session_regenerate_id();
        $_SESSION['bbphotoinitiated'] = true;
    }        
    
    if( isSet($_POST['action']) &&  $_POST['action'] == 'uploadPhoto' )
    {
	    //
	    // We are going to be asking permission to Ftp a picture to the server.
 	    //
	    header("Content-type: text/xml");
	    print("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n");  
	    print("<bbphotoresp>\n");
 	    
 	    //
 	    // Verify that the user is indeed logged in, and that the client's auth id is valid.
 	    //
        $origKey = $_POST['key'];
        $newKey = $origKey; 	

        if( isSet($origKey) && strlen($origKey) > 0 && $origKey == $_SESSION['bbphotoAdminsecureKey'] &&
            isSet( $_SESSION['userIsLoggedIn'] ) && $_SESSION['userIsLoggedIn'] == 'True' &&
            isSet( $_SESSION['username'] ) && strlen( trim( $_SESSION['username'] ) ) > 0 )

        {
           //
           // If so, generate a new secure key, and pass back all of the necessary information.	
           //
                  
           $newKey = generateKey( 10 );
           $_SESSION['bbphotoAdminsecureKey'] = $newKey;	

			print("<success>\n");
			print("<dir>/www/bbroerman.net/secure_images/temp/</dir>\n");
			print("<usr>bbroerman</usr>\n");
			print("<pass>n3wdrwh0</pass>\n");
			print("<newauth>" . $newKey . "</newauth>\n");
			print("</success>\n"); 	    	
		}
 	    else
 	    {
			//
			// Else, pass back an error message.
			//
			print("<failed> Not authorized to perform this action. </failed>\n");
 	    }
 	    
	    print("</bbphotoresp>\n");		   			
    }
 
function generateKey( $length )
{

  // start with a blank password
  $password = "";

  // define possible characters
  $possible = "0123456789bcdfghjkmnpqrstvwxyz"; 
    
  // set up a counter
  $i = 0; 
    
  // add random characters to $password until $length is reached
  while ($i < $length) 
  { 

    // pick a random character from the possible ones
    $char = substr($possible, mt_rand(0, strlen($possible)-1), 1);
        
    // we don't want this character if it's already in the password
    if (!strstr($password, $char)) 
    { 
      $password .= $char;
      $i++;
    }

  }

  // done!
  return $password;
}
?>