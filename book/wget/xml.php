<?php
p($_POST);
header('Content-Type: text/xml');
echo"<?xml version=\"1.0\" encoding=\"gbk\"?>";
//echo '';
?>
<root><![CDATA[
<?php
echo "post:";
 print_r($_POST)."\n";
echo "get:";
 print_r($_GET);

?>
]]></root>

<?php

function p(&$p){
    if(is_array($p)){
        foreach($p as $k=>$v){
            p($p[$k]);
        }
    }else{
        $p=unescape($p);
    }
}

/*处理由js处理的unescape*/
function unescape($str,$encode='gbk'){
    if(!function_exists('mb_convert_encoding')&& !function_exists('iconv') ) return $str;
    $r='';
    for($i=0,$c=strlen($str);$i<$c;$i++){
        $posS=strpos($str,'%u',$i);
        if($posS!==false&& $posS==$i){
            $v=substr($str,$posS+2,4);
            if(is_numeric(hexdec($v))) {
                if(function_exists('mb_convert_encoding')){
                    $r.= mb_convert_encoding(pack("H*",$v),$encode,"UCS-2");
                }elseif(function_exists('iconv')){
                    $r .= iconv("UCS-2",$encode,pack("H*",dechex($v)));
                }
                $i=$posS+5;
            }else $r.= $str{$i};
        }else{
            $r.= $str{$i};
        }
    }
    return $r;
}
?>