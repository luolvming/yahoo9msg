<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<HTML>
 <HEAD>
  <TITLE> 免费的动态域名解析 </TITLE>  
  <META NAME="Keywords" CONTENT="">
  <META NAME="Description" CONTENT="">
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
 </HEAD>

 <BODY>
 <div id="main">
	 <FORM METHOD="POST" ACTION="<?=$_SERVER['PHP_SELF']?>?do=add">
		用户名：<INPUT TYPE="text" NAME="name" maxlength="16">
		密码：<INPUT TYPE="password" NAME="password" maxlength="16">
		<INPUT TYPE="submit" value=" 注 册 ">
	 </FORM>
	 <div style="font-size:12px;padding-top:20px;">
	 说明:
	 <ul>
		<li>用户名 跟密码最长度在16位 </li>		
		<li>用户名 跟密码只支持数字跟英文的组合 </li>		
	 </ul>
	 </div>
 </div>
 </BODY>
</HTML>
