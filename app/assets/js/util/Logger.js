define(["config", "libs/underscore"], function (config, _) {

    "use strict";

    var Logger = [],

        console = window.console || {
            trace: function () {},
            debug: function () {},
            info: function () {},
            warn: function () {},
            error: function () {}
        };

    config.logs = config.logs || {};

    function newLogger(name) {

        function format(name, message) {
            return _.isString(message) ? ("[" + name + "] " + message) : message;
        }

        return {
            TRACE: 0,
            DEBUG: 1,
            INFO: 2,
            WARN: 3,
            ERROR: 4,
            SILENT: 5,
            level: _.isUndefined(config.logs[name]) ? Logger.globalLevel : config.logs[name],
            name: name || "GLOBAL",

            setLevel: function (level) {
                this.level = level;
            },

            trace: function (message) {
                if (this.level <= this.TRACE) {
                    console.debug(format(name, message));
                    console.trace();
                }
            },

            debug: function (message) {
                if (this.level <= this.DEBUG) {
                    console.debug(format(name, message));
                }
            },

            info: function (message) {
                if (this.level <= this.INFO) {
                    console.info(format(name, message));
                }
            },

            warn: function (message) {
                if (this.level <= this.WARN) {
                    console.warn(format(name, message));
                }
            },

            error: function (message) {
                if (this.level <= this.ERROR) {
                    console.error(format(name, message));
                }
            },

            log: function (message) { this.info(message); }
        };

    }

    Logger.globalLevel = _.isUndefined(config.logs.globalLevel) ? 4 : config.logs.globalLevel;

    Logger.of = function (name) {
        if (_.isUndefined(Logger[name])) {
            var logger = newLogger(name);
            Logger.push(name);
            Logger[name] = logger;
            return logger;
        } else {
            return Logger[name];
        }
    };

    window.Logger = Logger;

    return Logger;
});