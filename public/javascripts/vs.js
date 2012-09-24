(function (){

    var W3 = window.W3 = (window.W3 || {});

    var Socket = W3.Socket = {

        open: function (options) {
            var url, onmessage, ws;
            url = options && options.url ? "ws://" + window.location.host + options.url : "ws://" + window.location;
            onmessage = options && options.onmessage ? options.onmessage : undefined;
            ws = WebSocket ? new WebSocket(url) : {
                send: function (m){return false;},
                close: function (){}
            };
            if (_.isFunction(options.onmessage))
                ws.onmessage = options.onmessage;
            return ws;
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