# HTML code #

```
<!DOCTYPE>
<html xmlns="http://www.w3.org/1999/xhtml" lang="zh-cn" xml:lang="zh-cn">
<head>
<title>ice6 IE6方案</title>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<meta http-equiv="X-UA-Compatible" content="IE=EmulateIE8" />

<style>
html {_background-image: url(about:blank); _background-attachment: fixed;/* prevent screen flash in IE6 */}
body {background-attachment: scroll;}
#base_wrapper{position:fixed;
 z-index:9999999;
 bottom:0;
 width:100%;
 _position: absolute;
 _top: expression(eval(document.documentElement.scrollTop+document.documentElement.clientHeight-this.offsetHeight-(parseInt(this.currentStyle.marginTop,10)||0)-(parseInt(this.currentStyle.marginBottom,10)||0)));
 }

</style>

</head>
<body>
<div id="box" style="height:800px;">d</div>
<div id="base_wrapper">随便弄点东西进来吧，这个方案我感觉比开心网和人人网用js控制的方案好，起码不用去用fadeIn、fadeOut去修复抖动</div>
</body>
</html>

```