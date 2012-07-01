<?php
include "config.ini.php";
$id= intval($_REQUEST['id']);
//echo getKey($id)."<br>";
//echo getKey('5')."<br>";
$key= trim($_REQUEST['key']);
if($key!= getKey($id) ){
	die( "key_error");
}

$dns= $db->getRow("select * from rr where id='".$id."' ");
$ip= getIp();
//echo $ip;
if($ip == $dns['data']) {
	die("no change");
}else{
	$sql="update rr set data='".$ip."' where  id='".$id."'  ";
	$db->query( $sql );
	echo ("change to ". $ip);
}
?>