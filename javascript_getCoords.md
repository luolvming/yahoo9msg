# Introduction #

Add your content here.


# Details #
```
K.getCoords=function (node){
	var x = node.offsetLeft;
	var y = node.offsetTop;
	var parent = node.offsetParent;
	while (parent != null){
		x += parent.offsetLeft;
		y += parent.offsetTop;
		parent = parent.offsetParent;
	}
	return {
		x: x,
		y: y
	};
}
```