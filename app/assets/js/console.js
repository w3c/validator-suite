require(["util/console", "libs/jquery", "util/Util"], function (Console, $, Util) {

    "use strict";

    $(function () {
        var el = $(".console"),
            url = Util.resolveUrl(el.attr("data-socket"))
                .replace("http://", "ws://")
                .replace("https://", "wss://");
        window.Console = new Console(el, url);
    });

});