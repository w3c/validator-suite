define([], function () {

    "use strict";

    var W3 = {};

    W3.Logger = function (name) {

        return {
            info: function (msg) {
                if (console && console.info) { console.info("[" + name + "] " + msg); }
            },
            warn: function (msg) {
                if (console && console.warn) { console.warn("[" + name + "] " + msg); }
            },
            error: function (msg) {
                if (console && console.error) { console.error("[" + name + "] " + msg); }
            },
            debug: function (msg) {
                if (console && console.debug) { console.debug(msg); }
            },
            trace: function () {
                if (console && console.trace) { console.trace(); }
            }
        };

    };

    W3.Util = {

        shortenUrl: function (url, limit) {
            var shortUrl;
            shortUrl = url.replace("http://", "");
            return (shortUrl.length > limit ?
                    shortUrl.substring(0, limit / 2) + "…" + shortUrl.substring(shortUrl.length - limit / 2) :
                    shortUrl);
        },

        resolveUrl: function (url) {
            var a = document.createElement('a');
            a.setAttribute("href", url);
            return a.href;
        }

    };

    W3.Socket = function (url, type) {

        var logger, websocketProtocol, types, socket, implementations, i;

        logger = new W3.Logger("Socket");

        websocketProtocol = {
            "http://": "ws://",
            "https://": "wss://"
        };

        types = {
            1: "eventsource",
            0: "websocket",
            2: "comet"
        };

        socket = _.extend({ url: W3.Util.resolveUrl(url) }, Backbone.Events);

        implementations = {

            websocket: function () {
                var websocket, protocol;
                if (!window.WebSocket) {
                    throw new Error("WebSocket is not supported");
                }
                for (protocol in websocketProtocol) {
                    socket.url = socket.url.replace(protocol, websocketProtocol[protocol]);
                }
                logger.info("opening websocket: " + socket.url);
                websocket = new window.WebSocket(socket.url + '/ws');
                websocket.onmessage = function (event) {
                    var job = JSON.parse(event.data);
                    logger.info("New message: jobUpdate");
                    logger.debug(job);
                    socket.trigger("jobupdate", job);
                };
                websocket.onopen = function (event) {
                    logger.info("websocket opened");
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
                logger.info("opening server events socket");
                eventsource = new window.EventSource(socket.url + '/events');
                eventsource.onmessage = function (event) {
                    var job = JSON.parse(event.data);
                    logger.info("New message: jobUpdate");
                    logger.debug(job);
                    socket.trigger("jobupdate", job);
                };
                eventsource.onopen = function (event) {
                    logger.info("eventsource connection opened");
                    socket.trigger("open", event);
                };
                eventsource.onerror = function (event) {
                    logger.info("eventsource error");
                    logger.debug(event);
                    socket.trigger("error", event);
                };
                socket.close = function () {
                    eventsource.close();
                    logger.info("eventsource closed");
                    socket.trigger("close", new window.Event("close"));
                };
                socket.type = "eventsource";
            },

            comet: function () {
                var iframe;
                logger.warn("using comet iframe");
                iframe = $('<iframe src="' + socket.url + '/comet"></iframe>');
                $(function () {
                    setTimeout(function () {
                        $('body').append(iframe);
                        socket.trigger("open", new window.Event("open"));
                    }, 0);
                });
                socket.close = function () {
                    iframe.remove();
                    socket.trigger("close", new window.Event("close"));
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

    W3.Messages = function (msg) {



    };

    W3.exception = function (msg) {
        throw new Error(msg);
    };

    return W3;

});