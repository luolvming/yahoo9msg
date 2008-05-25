
function mout(id,color){
      id.style.backgroundColor=color;
}

function $(id){
	return document.getElementById(id);
}

function w(str){
	document.write(str);
}

/*读取cookie*/
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

function isUndefined(variable) {
	return typeof variable == 'undefined' ? true : false;
}
/*pop*/
function pop(){
	this.width="600";
	this.height="440";
	this.title='';

	this.show=function(o){
		//alert(document.body.offsetHeight+':'+document.body.scrollHeight+':'+document.body.clientHeight);
	  var sWidth,sHeight;
	  sHeight=document.body.clientHeight;	  
      sWidth=document.body.clientWidth-2;//document.body.offsetWidth-1;scrollWidth

      var bgObj=document.createElement("div");
      bgObj.setAttribute('id','bgDiv');
      bgObj.style.position="absolute";
      bgObj.style.top="0";
      bgObj.style.background="#000";
      bgObj.style.filter="progid:DXImageTransform.Microsoft.Alpha(style=3,opacity=25,finishOpacity=75";
      bgObj.style.opacity="0.6";
      bgObj.style.left="0";
      bgObj.style.width=sWidth + "px";
      bgObj.style.height=sHeight + "px";
      bgObj.style.zIndex = "10000";
	  bgObj.oncontextmenu=function(e){return false;} 
	 // bgObj.onclick=function a(){return false;}
      document.body.appendChild(bgObj);

	  var msgObj=document.createElement("div")
      msgObj.setAttribute("id","msgDiv");
         msgObj.style.position = "absolute";
         msgObj.style.left = "40%";
         msgObj.style.top = "30%";
         msgObj.style.font="12px/1.6em Verdana, Geneva, Arial, Helvetica, sans-serif";
         msgObj.style.marginLeft = "-225px" ;
         msgObj.style.marginTop = -75+document.documentElement.scrollTop+"px";
         msgObj.style.zIndex = "10001";
	     //msgObj.style.background="red";
	    
         height=(isUndefined(o)||isUndefined(o.height))?this.height:o.height;
		 width=(isUndefined(o)||isUndefined(o.width))?this.width:o.width;
		 title=(isUndefined(o)||isUndefined(o.title))?this.title:o.title;
		 var _html=(isUndefined(o)||isUndefined(o.html))?'您想干什么？':o.html;
		
   var __HTML ='<link rel="stylesheet" href="./css/flora.resizable.css" type="text/css" />\
   <link rel="stylesheet" href="./css/flora.dialog2.css" type="text/css" />\
	   <div class="flora" style="text-align:left;">\
	   <div tabindex="-1" class="ui-dialog ui-resizable" style="overflow: hidden; display: block; position: absolute; width:'+width+'px; height: '+height+'px; outline-color: invert; outline-style: none; outline-width: 0pt; top: 0px; left: 0px; z-index: 1003;">\
	      <div style="position: relative;" class="ui-dialog-container">\
	        <div class="ui-dialog-titlebar" >\
	           <span class="ui-dialog-title" onselectstart="return false;" style="-moz-user-select: none;">'+title+'</span>\
		    <a href="javascript:void(0)" class="ui-dialog-titlebar-close" onmouseover="javascript:this.style.background=\' url(image/i/dialog-titlebar-close-hover.png) no-repeat\';" onmouseout="javascript:this.style.background=\' url(image/i/dialog-titlebar-close.png) no-repeat\';" \
	       onclick="\
             document.body.removeChild($(\'bgDiv\'));\
             document.body.removeChild( $(\'msgDiv\') );"><span>X</span></a>\
	   </div>\
	   <div class="ui-dialog-content" id="example2" title="dialog title">'+_html+'</div>\
	</div>\
	<div style="-moz-user-select: none;" class="ui-resizable-n ui-resizable-handle"></div>\
	<div style="-moz-user-select: none;" class="ui-resizable-s ui-resizable-handle"></div>\
	<div style="-moz-user-select: none;" class="ui-resizable-e ui-resizable-handle"></div>\
	<div style="-moz-user-select: none;" class="ui-resizable-w ui-resizable-handle"></div>\
	<div style="-moz-user-select: none;" class="ui-resizable-ne ui-resizable-handle"></div>\
	<div style="-moz-user-select: none;" class="ui-resizable-se ui-resizable-handle"></div>\
	<div style="-moz-user-select: none;" class="ui-resizable-sw ui-resizable-handle"></div>\
	<div style="-moz-user-select: none;" class="ui-resizable-nw ui-resizable-handle"></div>\
</div>\
</div>';
   
	  msgObj.innerHTML='a'+__HTML;
      document.body.appendChild(msgObj);
	}
}

/*计算字符大小*/
function mb_strlen(str) {
	if(str==null) return -1;
	var len = 0;
	for(var i = 0; i < str.length; i++) {
		len += str.charCodeAt(i) < 0 || str.charCodeAt(i) > 255 ? 2 : 1;
	}
	return len;
}