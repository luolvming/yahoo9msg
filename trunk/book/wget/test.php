<?php
 //require_once(dirname(__FILE__)."/../config.php");
 function subString($string,$stext,$etext){
	$spos=strpos($string,$stext);
	if($spos===false)return '';
	$epos=strpos($string,$etext,$spos);
	if($epos===false)return '';
	
    $stPos=(strlen($stext)+$spos);    
	return substr($string,$stPos,($epos-$stPos));
	
}
 //È¡µÃÄÚÈÝ
 /*
 $str=file_get_contents(dirname(__FILE__)."/context.htm");
 //die($str);
 $context=subString($str,'id="contTxt">','</div>');
 $context = strtr($context,array("\n"=>'',"\r"=>'',"\t"=>''));
 echo $context;
 */

 $str = file_get_contents(dirname(__FILE__)."/vip.htm");

 preg_match_all('/<a href="chapter_(\d+)_(\d+).html(.*?)<img/i',$str,$mat);
 print_r($mat);


?>