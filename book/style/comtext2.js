document.oncontextmenu=function(e){return false;}
document.onselectstart=function(e){return false;}
function popUp(e){e=e?e:window.event;menu=document.getElementById('mmenu');if(e.button==2){newX=e.x+document.body.scrollLeft;newY=e.y+document.body.scrollTop;menu.style.display=""
menu.style.pixelLeft=newX;menu.style.pixelTop=newY;}
else{menu.style.display="none";}}
var isAllCreen=false;function AllScreen(id){if(!isAllCreen){var header=$('header');header.style.display="none";var header=$('Sidebar');header.style.display="none";var header=$('MainBody');header.style.width="100%";id.value="ȡ �� ȫ ��";isAllCreen=true;}else{isAllCreen=false;var header=$('header');header.style.display="block";var header=$('Sidebar');header.style.display="block";var header=$('MainBody');header.style.width="740px";id.value="ȫ ��";}}
function changeBackGround(id){var bid=$('Context');bid.style.backgroundColor=id.value;addCookie('bkColor',id.value);}
function changeFontSize(id){var bid=$('Context');var size=12;switch(id.value){case"0":return;case"1":case"3":size='13';break;case"2":size='12';break;case"4":size='14';break;case"5":size='16';break;}
bid.style.fontSize=size+"px";addCookie('fontSize',size);}
function next(upUrl,downUrl){document.onkeydown=function key(e){e=e?e:window.event;if(e.keyCode==37)location.href=upUrl;if(e.keyCode==39||e.keyCode==13)location.href=downUrl;}}
function _init(){var id=$('Context');var bkcolor=getCookie('bkColor');if(bkcolor==null){addCookie('bkColor',"#F7F7F7");bkcolor="#F7F7F7";}
id.style.backgroundColor=bkcolor;var fontSize=getCookie('fontSize');if(fontSize==null){addCookie('fontSize',"13");fontSize="13";}
id.style.fontSize=fontSize+"px";;}