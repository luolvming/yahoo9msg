#服务器config配置

# nginx config #

user  www www;
worker\_processes  8;

error\_log   /data/logs/nginx/error.log;
#error\_log  logs/error.log  notice;
#error\_log  logs/error.log  info;
#pid        logs/nginx.pid;
events {
> use epoll;
> worker\_connections  51200;
}


http {
> include       mime.types;
> default\_type  application/octet-stream;
> sendfile        on;
> tcp\_nopush     on;
> server\_names\_hash\_bucket\_size 128;

> access\_log off;
#    access\_log logs/access\_log.log;
> keepalive\_timeout  60;
> tcp\_nodelay on;

> gzip  on;
> include vhosts/**;**

> fastcgi\_connect\_timeout 3000;
> fastcgi\_send\_timeout 3000;
> fastcgi\_read\_timeout 3000;


}


# vhost/dj.iciba.com #

server
{
> listen 80;
> server\_name dj.iciba.com admin.dj.iciba.com 60.28.216.6 192.168.0.6;
> index index.html index.php index.shtml index.htm ;
> root  /data/app/dj.iciba.com/webapp/dj.iciba.com/wwwroot;
#   error\_page 404 http://$server_name/404.html;
> rewrite "^/search(._)" /djsearch.php$1;
rewrite "^/list**([0-9]**)_([0-9]**)\.shtml$" /list.php?type=$1&page=$2;
rewrite "^/write/cat\.html$" /v5/index.php?mod=write;
rewrite "^/write/zm\.html" /v5/index.php?mod=write&zm=1;
rewrite "^/synonym/([a-z])\.html" /v5/index.php?mod=synonym&zm=$1;
if (!-f $request\_filename)
{
> rewrite "<sup>/([</sup>\/]+)/?$" /v5/djsearchV5.php?s=$1;
  1. ewrite "<sup>/([</sup>\/]+)$" /djsearch.php?s=$1;
  1. ewrite "<sup>/([</sup>\/]+)/$" /djsearch.php?s=$1;
}
> location ~ .**\.php?$
> {
> > include fastcgi\_judian;

> }
> > location ~**.(jpg|gif|png|js)$ {
> > > if (-f $request\_filename) {
> > > > expires max;
> > > > break;

> > > }

> > }
}**

# fastcgi\_judian config #

fastcgi\_ignore\_client\_abort  on;
fastcgi\_pass   127.0.0.1:9001;
fastcgi\_index  index.php;

fastcgi\_param  QUERY\_STRING       $query\_string;
fastcgi\_param  REQUEST\_METHOD     $request\_method;
fastcgi\_param  CONTENT\_TYPE       $content\_type;
fastcgi\_param  CONTENT\_LENGTH     $content\_length;
fastcgi\_param  SCRIPT\_FILENAME    $document\_root$fastcgi\_script\_name;

fastcgi\_param  SCRIPT\_NAME        $fastcgi\_script\_name;
fastcgi\_param  REQUEST\_URI        $request\_uri;
fastcgi\_param  DOCUMENT\_URI       $document\_uri;
fastcgi\_param  DOCUMENT\_ROOT      $document\_root;
fastcgi\_param  SERVER\_PROTOCOL    $server\_protocol;

fastcgi\_param  GATEWAY\_INTERFACE  CGI/1.1;
fastcgi\_param  SERVER\_SOFTWARE    nginx/$nginx\_version;

fastcgi\_param  REMOTE\_ADDR        $remote\_addr;
fastcgi\_param  REMOTE\_PORT        $remote\_port;
fastcgi\_param  SERVER\_ADDR        $server\_addr;
fastcgi\_param  SERVER\_PORT        $server\_port;
fastcgi\_param  SERVER\_NAME        $server\_name;

# PHP only, required if PHP was built with --enable-force-cgi-redirect
fastcgi\_param  REDIRECT\_STATUS    300;

