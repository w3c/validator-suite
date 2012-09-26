(function (){

    var W3 = window.W3 = (window.W3 || {});

    var Socket = W3.Socket = {

        socketProtocol: {
            "http:": "ws://",
            "https:": "wss://"
        },

        callback: function(e) {
            console.log(e);
        },

        open: function (options) {

            var url, onmessage, socket, cometsocket;

            //this.socketProtocol[location.protocol] + location.host +

            url = options && options.url ? options.url : "";

            onmessage = options && _.isFunction(options.onmessage) ? options.onmessage : function () {};

            cometsocket = function (url) {

                $(function () {
                    var iframe = $('<iframe src="' + url + '"></iframe>');
                    setTimeout(function () {
                        $('body').append(iframe);
                    }, 10);
                });

                return {

                };
            };

            window.W3.Socket.callback = onmessage;

            if (WebSocket) {
                socket = new WebSocket(this.socketProtocol[location.protocol] + location.host + url + '/ws');
                socket.onmessage = function (event) {
                    return onmessage($.parseJSON(event.data));
                };
                socket.onerror = function (e) {
                    console.log(e);
                    new cometsocket(url + '/comet');
                };
            } else {
                console.log("falling back to comet");
                socket = new cometsocket(url + '/comet');
            }

            return socket;
        }

    };

    var shorten = W3.shorten = function (string, limit) {
        var _string, dif;
        _string = string.replace("http://", "");
        if (_string.length > limit) {
            dif = _string.length - limit
            return _string.substring(0, limit/2) + "…" +
                   _string.substring(string.length - limit/2)
        } else {
            return _string
        }
    }

})();