require([
    "util/Console",
    "libs/jquery",
    "util/Util",
    "libs/foundation",
    "libs/foundation.dropdown"], function (Console, $, Util) {

    "use strict";
    $(document).foundation();
    $(function () {
        var el = $(".console"),
            url = Util.resolveUrl(el.attr("data-socket"))
                .replace("http://", "ws://")
                .replace("https://", "wss://");
        window.Console = new Console(el, url);
    });

});