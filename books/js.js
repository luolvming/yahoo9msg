
function mout(id,color){
      id.style.backgroundColor=color;
}

function $(id){
	return document.getElementById(id);
}

function w(str){
	document.write(str);
}

/*∂¡»°cookie*/
function getCookie(str){
	var tmp,reg=new RegExp("(^| )"+str+"=([^;]*)(;|$)","gi");
	if(tmp=reg.exec(document.cookie))return(tmp[2]);
	return null;
}
function delCookie(str){
	document.cookie=str+"=";
}
function addCookie(para,value){
	document.cookie=para+"="+value;
}