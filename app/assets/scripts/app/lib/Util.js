define(["lib/Logger"], function (Logger) {

    "use strict";

    var logger = new Logger("Util");

    return {

        getTemplate: function (name) {
            try {
                logger.info("Getting template: " + name);
                return _.template(document.getElementById(name).text);
            } catch (ex) {
                //console.log(ex);
                logger.error("Error getting " + name + " template: " + ex.message);
                return _.template("");
            }
        },

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
        },

        exception: function (msg) {
            throw new Error(msg);
        },

        valueFrom: function (elem) {

            //var $elem = $(elem);
            return function (attribute) {
                var tag = elem.find('[' + attribute + ']'),
                    attr = tag.attr(attribute);
                if (attr !== "") {
                    return attr;
                } else {
                    return tag.text();
                }
            };
        }

    };

});