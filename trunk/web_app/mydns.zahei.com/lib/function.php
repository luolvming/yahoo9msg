<?php
    function getIp()
    { 
        if (@$_SERVER['HTTP_CLIENT_IP'] && $_SERVER['HTTP_CLIENT_IP']!='unknown') {   
            $ip = $_SERVER['HTTP_CLIENT_IP'];
        } elseif (@$_SERVER['HTTP_X_FORWARDED_FOR'] && $_SERVER['HTTP_X_FORWARDED_FOR']!='unknown') { 
            $ip = $_SERVER['HTTP_X_FORWARDED_FOR'];   
        } else {   
            $ip = $_SERVER['REMOTE_ADDR'];   
        }   
        return $ip;  
    }

	function getKey($id){
		return substr(md5('zahei_'.$id.'_coms') ,0,16);
	}

	function msg($msg,$url=""){
		header("Content-type: text/html; charset=utf-8");
		$url = $url==""?"javascript:history.go(-1);":$url;
		echo "<a href='".$url."'>".$msg."</a>";
		die();
	}

	function uCheck(){
		global $db;
		$username= $_SERVER['PHP_AUTH_USER'];
		$password= $_SERVER['PHP_AUTH_PW'];
		//if(! ($_SERVER['PHP_AUTH_USER']=='shadow' && $_SERVER['PHP_AUTH_PW']=='dooy')){
		$sql=" select user_id from user where username='".$username."' and password='".$password."'  ";
		$user_id= $db->getOne( $sql );
		if( ! $user_id ){
			Header(  "WWW-Authenticate:   Basic   realm=Login");
			die('Account or password error ,Pleace email to  admin@zahei.com ;<a href="javascript:history.go(-1)">back</a>');
		}
		return $user_id;
	}
?>
