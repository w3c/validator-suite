define(["util/Logger", "libs/underscore", "libs/jquery"], function (Logger, _, $) {

    "use strict";

    // Polyfill for String.startsWith
    if (!String.prototype.startsWith) {
        Object.defineProperty(String.prototype, 'startsWith', {
            enumerable: false,
            configurable: false,
            writable: false,
            value: function (searchString, position) {
                position = position || 0;
                return this.indexOf(searchString, position) === position;
            }
        });
    }

    var logger = Logger.of("Util"),
        templates = {};

    return {

        getTemplate: function (name) {
            if (templates[name]) {
                //logger.debug("Getting template: " + name + " from cache");
                return templates[name];
            }
            try {
                logger.info("Getting template: " + name);
                templates[name] = _.template(document.getElementById(name).text);
                return templates[name];
            } catch (ex) {
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
            elem = $(elem);
            return function (attribute) {
                var result = $('[' + attribute + ']', elem).map(function (i, sub) {
                    //var tag = elem.find('[' + attribute + ']'),
                    var attr = $(sub).attr(attribute);
                    if (attr !== "") {
                        return attr;
                    } else {
                        return $(sub).html().toString();
                    }
                }).toArray();

                if (result.length === 0) {
                    return undefined;
                }
                if (result.length === 1) {
                    return result[0];
                }
                return result;
            };
        },

        getValue: function (url, obj) {
            if (_.isFunction(url)) {
                return _.bind(url, obj)();
            } else {
                return url;
            }
        },

        getAssertorName: function (assertorId) {
            /*
             assertor.checker_i18n=I18n Checker
             assertor.validator_nu=HTML5 Validator
             assertor.validator_css=CSS Validator
             assertor.markup_validator=HTML4/XHTML Validator
             */

            switch (assertorId) {
            case "checker_i18n":
                return "Internationalization";
            case "validator_nu":
                return "HTML5";
            case "validator_css":
                return "CSS";
            case "markup_validator":
                return "HTML4/XHTML Validator";
            default:
                throw new Error("Unable to find assertor name for: " + assertorId);
            }
        }

    };

});