<html>
<head>
  <title> FTP Test Login Service </title>
</head>
<body>
<?php
    session_start();

    //
    // Try to prevent session fixation attack.
    //
    if (!isset($_SESSION['someuniquewierdkey']))
    {
        session_regenerate_id();
        $_SESSION['someuniquewierdkey'] = true;
    }        
    
    $returnCode = true;
    
    if( !isSet($_POST['usrname']) || !isSet($_POST['passwd']) ||
        strlen($_POST['usrname']) == 0 || strlen($_POST['passwd']) == 0 )
    {    
    	printLoginForm( );
    }
    else
    {
  		//
		// We're going to validate the input username and password against the values stored in the database, and if OK,
		// we will set up the session data, and the admin link on the parent webpage.
		//	
   		$returnCode = dbConnect('<Database Username>','<Database Password>');
   	 	if( true == $returnCode )
    	{
	    	// Check that the username / password are correct.
	    	list($returnCode, $userid) = validateUser( $_POST['usrname'], $_POST['passwd'] );
	    
	    	if( true == $returnCode )
	    	{
		    	$_SESSION['someuniquewierdkey'] = 'True';
	    		$_SESSION['userIsLoggedIn'] = 'True';
    			$_SESSION['username'] = trim($_POST['usrname']);
    			$_SESSION['userid'] = $userid;
            	$_SESSION['photoAdminsecureKey'] = generateKey( 10 );
            
            	print("<h3 id='success'> ".$_SESSION['photoAdminsecureKey']." </h3>");
            	print("<script type='text/javascript'> window.opener.setKey('". $_SESSION['photoAdminsecureKey'] ."'); window.close();</script>");
	    	}
	    	else
	    	{
	    		printLoginForm( );
	    	}
    	}
    	else
    	{
    		print("<h3 id='error'> Unable to log into database. </h3>");
    	}

    	dbClose( );
    }


 function printLoginForm( )
 {
 	?>
 	<br>
 	<h3> Please Log In: </h3>
 	<form action='<?=$_SERVER['PHP_SELF']?>' method='post'>
 	   <b>User Name:</b> &nbsp; <input type='text' name='usrname'/><br>
 	   <b>Password: </b> &nbsp; <input type='password' name='passwd'/><br>
 	   <input type='submit' name='Submit'/>
 	</form>
 	<?php
 }
 
 function validateUser( $username, $password )
 {
   $returnCode = false;
   $userid = "";

   $username = htmlspecialchars($username);

   //
   // Protect against SQL injection attack.
   //

   if( true == checkForSqlInj($username) )
   {
       return false;
       print("<h3 id='error'> Bad username. </h3>");
   }

   if( true == checkForSqlInj($password) )
   {
       return false;
       print("<h3 id='error'> Bad password. </h3>");
   }

   //
   // Now, do the query...
   //

   $select = "Select usr_id, password from ftptest_users where usr_name = '".mysql_real_escape_string($username)."' ";

   $result=mysql_query($select);

   if( $result && mysql_num_rows( $result ) > 0 )
   {
     $row = mysql_fetch_assoc( $result );

     $cryptPwd = sha1($password);

     if( $row['password'] == $cryptPwd )
     {
       $returnCode = true;
       $userid = $row['usr_id'];
     }
     else
     {
         print("<h3 id='error'> Unable to find password. </h3>");
     }
   }
   else
   {
     print("<h3 id='error'> Unable to find username. </h3>");
   }

   //
   // Clean-up memory...
   //
   if( $result )
   {
     mysql_free_result( $result );
   }
   
   return array( $returnCode, $userid );
 }
 
 function checkForSqlInj( $value )
 {
   if( ( 0 != preg_match("/[\{\)=]+/",$value)) ||
       (FALSE != stristr($value, "INSERT")) ||
       (FALSE != stristr($value, "DELETE")) ||
       (FALSE != stristr($value, "DROP")) ||
       (FALSE != stristr($value, "SELECT")) ||
       (FALSE != stristr($value, "UPDATE")) ||
       (FALSE != stristr($value, "TRUNC")) ||
       (FALSE != stristr($value, "WHERE")) ||
       (FALSE != stristr($value, "FROM")) )
   {
     return true;
   }

   return false;
 }
  
 function dbClose( )
 {
   global $database_connection;

   @mysql_close( $database_connection );
 }

 function dbConnect( $username, $password )
 {
   global $database_connection;

   //
   // Connect to the database.
   //
   // Return TRUE if successful, or FALSE if not.
   //

   $returnCd = false;
   $database_connection = @mysql_connect(localhost,$username, $password);

   if( $database_connection )
   {
     $db = @mysql_select_db( '<database name>', $database_connection);

     if( $db )
     {
         $returnCd = true;
     }
   }
     
   return $returnCd;
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
</body>
</html>