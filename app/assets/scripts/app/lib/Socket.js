define(["lib/Logger", "lib/Util", "libs/backbone"], function (Logger, Util, Backbone) {

    "use strict";

    var logger = new Logger("Socket"),
        Socket;

    Socket = function (url, type) {

        logger.trace();

        var websocketProtocol, types, socket, implementations, i;

        websocketProtocol = {
            "http://": "ws://",
            "https://": "wss://"
        };

        types = {
            0: "eventsource",
            1: "websocket",
            2: "comet"
        };

        socket = _.extend({ url: Util.resolveUrl(url) }, Backbone.Events);

        implementations = {

            websocket: function () {
                var websocket, protocol;
                if (!window.WebSocket) {
                    throw new Error("WebSocket is not supported");
                }
                for (protocol in websocketProtocol) {
                    socket.url = socket.url.replace(protocol, websocketProtocol[protocol]);
                }
                websocket = new window.WebSocket(socket.url + '/socket/ws');
                websocket.onmessage = function (event) {
                    var message = JSON.parse(event.data);
                    socket.trigger("message", message);
                };
                websocket.onopen = function (event) {
                    logger.info("websocket opened: " + socket.url + '/socket/ws');
                    socket.trigger("open", event);
                };
                websocket.onerror = function (event) {
                    logger.error("websocket error");
                    logger.debug(event);
                    socket.trigger("error", event);
                };
                websocket.onclose = function (event) {
                    logger.info("websocket closed");
                    socket.trigger("close", event);
                };
                socket.close = function () { websocket.close(); };
                socket.type = "websocket";
            },

            eventsource: function () {
                var eventsource;
                if (!window.EventSource) {
                    throw new Error("EventSource is not supported");
                }
                eventsource = new window.EventSource(socket.url + '/socket/events');
                eventsource.onmessage = function (event) {
                    var message = JSON.parse(event.data);
                    socket.trigger("message", message);
                };
                eventsource.onopen = function (event) {
                    logger.info("eventsource connection opened: " + socket.url + '/socket/events');
                    socket.trigger("open", event);
                };
                eventsource.onerror = function (event) {
                    logger.info("eventsource connection error");
                    logger.debug(event);
                    socket.trigger("error", event);
                };
                socket.close = function () {
                    eventsource.close();
                    logger.info("eventsource connection closed");
                    socket.trigger("close");
                };
                socket.type = "eventsource";
            },

            comet: function () {
                var iframe;
                logger.warn("using comet iframe");
                iframe = $('<iframe src="' + socket.url + '/socket/comet"></iframe>');
                $(function () {
                    setTimeout(function () {
                        $('body').append(iframe);
                        socket.trigger("open");
                    }, 0);
                });
                socket.close = function () {
                    iframe.remove();
                    socket.trigger("close");
                };
                socket.type = "comet";
            }
        };

        //socket.fetch()

        if (type) {
            implementations[type]();
        } else {
            for (i in types) {
                try {
                    implementations[types[i]]();
                    break;
                } catch (ex) {
                    logger.warn(ex.message);
                    //continue;
                }
            }
        }

        return socket;
    };

    return Socket;

});