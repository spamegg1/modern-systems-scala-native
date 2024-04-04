# HTTP request / response example in curl

```bash
$ curl -v www.example.com
*   Trying 93.184.216.34:80...
* Connected to www.example.com (93.184.216.34) port 80 (#0)
> GET / HTTP/1.1
> Host: www.example.com
> User-Agent: curl/7.81.0
> Accept: */*
>
* Mark bundle as not supporting multiuse
< HTTP/1.1 200 OK
< Accept-Ranges: bytes
< Age: 534948
< Cache-Control: max-age=604800
< Content-Type: text/html; charset=UTF-8
< Date: Sun, 31 Mar 2024 14:36:18 GMT
< Etag: "3147526947"
< Expires: Sun, 07 Apr 2024 14:36:18 GMT
< Last-Modified: Thu, 17 Oct 2019 07:18:26 GMT
< Server: ECS (dce/26C1)
< Vary: Accept-Encoding
< X-Cache: HIT
< Content-Length: 1256
<
```

```html
<!doctype html>
<html>
<head>
    <title>Example Domain</title>

    <meta charset="utf-8" />
    <meta http-equiv="Content-type" content="text/html; charset=utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <style type="text/css">
    body {
        background-color: #f0f0f2;
        margin: 0;
        padding: 0;
        font-family: -apple-system, system-ui, BlinkMacSystemFont, "Segoe UI", "Open Sans", "Helvetica Neue", Helvetica, Arial, sans-serif;

    }
    div {
        width: 600px;
        margin: 5em auto;
        padding: 2em;
        background-color: #fdfdff;
        border-radius: 0.5em;
        box-shadow: 2px 3px 7px 2px rgba(0,0,0,0.02);
    }
    a:link, a:visited {
        color: #38488f;
        text-decoration: none;
    }
    @media (max-width: 700px) {
        div {
            margin: 0 auto;
            width: auto;
        }
    }
    </style>
</head>

<body>
<div>
    <h1>Example Domain</h1>
    <p>This domain is for use in illustrative examples in documents. You may use this
    domain in literature without prior coordination or asking for permission.</p>
    <p><a href="https://www.iana.org/domains/example">More information...</a></p>
</div>
</body>
</html>
* Connection #0 to host www.example.com left intact
```

## Testing the HTTP Client binary

### First, run with a fake netcat server

- Run it in two Terminal windows with:
- First window:

```bash
$ nc -l -v 127.0.0.1 8081
Listening on localhost 8081
Connection received on localhost 58200
GET / HTTP/1.1
Host: 127.0.0.1
```

- Second window:

```bash
$ ./path/to/binary/file 127.0.0.1 8081 /
looking up address: 127.0.0.1 port: 8081
about to perform lookup
lookup returned 0
got addrinfo: flags 2, family 0, socktype 1, protocol 6
creating socket
socket returned fd 3
connecting
connect returned 0
wrote request
reading status line?
```

- It will hang waiting.

### Next, run it with a real website

- Only one Terminal window this time:

```bash
./path/to/binary/file www.example.com 80 /

looking up address: www.example.com port: 80
about to perform lookup
lookup returned 0
got addrinfo: flags 2, family 0, socktype 1, protocol 6
creating socket
socket returned fd 3
connecting
connect returned 0
wrote request
reading status line?
parsing status
reading first response header
about to sscanf line: 'Age: 257064
'
(Age:,257064)
reading header
about to sscanf line: 'Cache-Control: max-age=604800
'
(Cache-Control:,max-age=604800)
reading header
about to sscanf line: 'Content-Type: text/html; charset=UTF-8
'
(Content-Type:,text/html;)
reading header
about to sscanf line: 'Date: Thu, 04 Apr 2024 09:25:04 GMT
'
(Date:,Thu,)
reading header
about to sscanf line: 'Etag: "3147526947+ident"
'
(Etag:,"3147526947+ident")
reading header
about to sscanf line: 'Expires: Thu, 11 Apr 2024 09:25:04 GMT
'
(Expires:,Thu,)
reading header
about to sscanf line: 'Last-Modified: Thu, 17 Oct 2019 07:18:26 GMT
'
(Last-Modified:,Thu,)
reading header
about to sscanf line: 'Server: ECS (dce/26AB)
'
(Server:,ECS)
reading header
about to sscanf line: 'Vary: Accept-Encoding
'
(Vary:,Accept-Encoding)
reading header
about to sscanf line: 'X-Cache: HIT
'
(X-Cache:,HIT)
reading header
about to sscanf line: 'Content-Length: 1256
'
(Content-Length:,1256)
reading header
saw content-length
got Response: HttpResponse(200,HashMap(Date: -> Thu,, Vary: -> Accept-Encoding, Age: -> 257064, Content-Length: -> 1256, Cache-Control: -> max-age=604800, Etag: -> "3147526947+ident", X-Cache: -> HIT, Server: -> ECS, Last-Modified: -> Thu,, Expires: -> Thu,, Content-Type: -> text/html;), ...)
```

```html
<!doctype html>
<html>
<head>
    <title>Example Domain</title>

    <meta charset="utf-8" />
    <meta http-equiv="Content-type" content="text/html; charset=utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <style type="text/css">
    body {
        background-color: #f0f0f2;
        margin: 0;
        padding: 0;
        font-family: -apple-system, system-ui, BlinkMacSystemFont, "Segoe UI", "Open Sans", "Helvetica Neue", Helvetica, Arial, sans-serif;

    }
    div {
        width: 600px;
        margin: 5em auto;
        padding: 2em;
        background-color: #fdfdff;
        border-radius: 0.5em;
        box-shadow: 2px 3px 7px 2px rgba(0,0,0,0.02);
    }
    a:link, a:visited {
        color: #38488f;
        text-decoration: none;
    }
    @media (max-width: 700px) {
        div {
            margin: 0 auto;
            width: auto;
        }
    }
    </style>
</head>

<body>
<div>
    <h1>Example Domain</h1>
    <p>This domain is for use in illustrative examples in documents. You may use this
    domain in literature without prior coordination or asking for permission.</p>
    <p><a href="https://www.iana.org/domains/example">More information...</a></p>
</div>
</body>
</html>
```
