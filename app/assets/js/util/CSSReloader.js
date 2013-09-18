
define(["libs/jquery", "libs/underscore"], function () {

    var CSSReloader = {};

    window.CSSReloader = CSSReloader;

    CSSReloader.watch = function (time) {

        var time = time ? time : 1000;

        if (CSSReloader.trigger)
            CSSReloader.stop();

        var head = $('head');

        if (!CSSReloader.styles) {
            CSSReloader.styles = $('head link[rel=stylesheet]').map(function (i, link) {
                var styleElem = $('<style data-url="' + link.href + '"></style>').appendTo(head);
                $.ajax({
                    url: link.href,
                    success: function (data) {
                        styleElem.text(CSSReloader.rewriteUrls(data, link.href));
                        $(link).remove();
                    }
                });
                return {
                    url: link.href,
                    styleElem: styleElem
                };
            });
        }

        CSSReloader.trigger = setInterval(function () {
            _.each(CSSReloader.styles, function (style) {
                $.ajax({
                    url: style.url,
                    success: function (data) {
                        var rewrite = CSSReloader.rewriteUrls(data, style.url);
                        if (rewrite !== style.styleElem.text()) {
                            console.log("[" + new Date().toLocaleTimeString() + "] Updating stylesheet: " + style.url);
                            style.styleElem.text(CSSReloader.rewriteUrls(data, style.url));
                        }
                    }
                });
            });
        }, time);

        return true;
    };

    CSSReloader.stop = function () {
        clearInterval(CSSReloader.trigger);
        delete CSSReloader.trigger;
        return true;
    };

    CSSReloader.rewriteUrls = function (data, href) {
        // rewrite imports too
        return data.replace(/url\(['"]?((?!http)[^'")]+)['"]?\)/g, "url(" + href.replace(/\/[^\/]+$/, "/") + "$1)");
    };

    return CSSReloader;

});
