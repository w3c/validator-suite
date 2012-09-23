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

})();