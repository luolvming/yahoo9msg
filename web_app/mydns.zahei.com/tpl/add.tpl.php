<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<HTML>
 <HEAD>
  <TITLE>管理免费动态域名 </TITLE>  
  <META NAME="Keywords" CONTENT="">
  <META NAME="Description" CONTENT="">
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
 </HEAD>

 <BODY>
 <div id="main">
	 <FORM METHOD="POST" ACTION="<?=$_SERVER['PHP_SELF']?>?do=add">
		<INPUT TYPE="text" NAME="name" maxlength="10" style="width:100px;">.mvp5.com		<INPUT TYPE="submit" value=" 添加动态域名 ">
	 </FORM>
	 <div style="font-size:12px;padding-top:20px;">
	 说明:
	 <ul>
		<li>动态域名必须是字母、数字 或者是字母跟数字的组合 </li>				
		<li>动态域名字符长度必须在3-10内 </li>				
	 </ul>
	 </div>
	 <hr>
	<?php if($mydns){ ?>
	 <div>
		 <table width="400">
		 <tr><th>域名</th><th>ip</th><th>操作</th></tr>
		 <?php foreach($mydns as $k=>$v){ ?>
		 <tr><td><?=$v['name']?>.mvp5.com</td><td><?=$v['data']?></td><td style="font-size:12px;">[<a href="<?=$_SERVER['PHP_SELF']?>?do=del&id=<?=$v['id']?>" onclick="javascript:return confirm('您确定要删除？');">删除</a>] [<a href="./api.php?id=<?=$v['id']?>&key=<?=getKey($v['id'])?>" target="_blank">取得更新链接</a>]</td>
		 </tr>
		 <?php  } ?>
		 </table>
	 </div>

	 <div style="font-size:12px;padding-top:20px;">
	 使用说明:
	 <ul>
		<li>您客户端只需要一改变ip时，访问下“更新链接”</li>				
		<li>如果您是Linux用户只需要在 crontab 里面添加“ */2 * * * * wget http://dns.zahei.com/api.php?id=$id&key=$key ”;表示每2分钟更新一次ip。注:$id $key 是变量,具体的链接可以点击“取得更新链接”得到</li>				
	 </ul>
	 </div>

	 <?php } ?>
 </div>

 </BODY>
</HTML>
