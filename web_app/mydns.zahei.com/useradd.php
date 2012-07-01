<?php
include "config.ini.php";
//$user_id= uCheck();
//if($user_id>0) msg("您已经是注册用户，请进入管理!","add.php");
if(isset($_REQUEST['do']) && $_REQUEST['do']=='add'){
	$username= trim($_POST['name']);
	$password= trim($_POST['password']);
	if(!$username || !$password ){
		msg('用户名、密码都不允许为空' );
	}
	$sql="select count(*) as cnt from user where username  ='".$username."' ";
	$cnt= $db->getOne($sql);
	if($cnt>0) msg('用户名 被站用');	
	$sql="insert into user set username  ='".$username."' , password='".$password."' ";
	$db->query($sql);
	msg("注册成功，进入管理","add.php");
}
include "tpl/useradd.tpl.php";
?>