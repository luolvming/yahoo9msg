<?php
include "config.ini.php";
$user_id= uCheck();
$do=$_REQUEST['do'] ;

if($do=='add'){
	$name= strtolower($_REQUEST['name']);
	$c= strlen($name);
	if($c<3 || $c>10)msg("域名字符长度必须在3-10内");
	if( ereg("[^0-9a-z]",$name) )msg("动态域名必须是字母、数字 或者字母跟数字的组合");
	$cnt=$db->getOne("select count(*) as cnt from rr where  zone=1 and name='".$name."'  ");
	if($cnt>0) msg("此域名被添加，请使用其他的域名。");
	$ip=getIp();
	$sql="insert into rr set zone=1,name='".$name."',type='A',data='".$ip."',ttl='300' ";
	$db->query($sql);	
	$id= $db->lastID();
	$sql="insert into user_rr set rr_id='".$id."',user_id='".$user_id."'  ";
	$db->query( $sql );
}
if($do=='del'){
	$id= intval($_REQUEST['id']);
	$cnt = $db->getOne("select count(*) as cnt from  user_rr where  rr_id='".$id."'and user_id='".$user_id."' ");
	if($cnt==0) msg("改域名不存在或者非本人域名");
	$sql="delete from user_rr where rr_id='".$id."'";
	$db->query($sql);
	$sql="delete from rr where id='".$id."'";
	$db->query($sql);
	msg("删除成功",$_SERVER['PHP_SELF']);
}
 
$mydns= $db->getAll("select * from rr where id in (select rr_id from user_rr where user_id='".$user_id."' ) order by id desc");



include "tpl/add.tpl.php";
?>