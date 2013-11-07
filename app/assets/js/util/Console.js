define(["util/Socket", "util/Logger", "libs/jquery", "libs/underscore"], function (Socket, Logger, $, _) {

    "use strict";

    var invite = "> ",
        History,
        Console,
        logger = Logger.of("Console");

    History = function () {

        var array = [],
            index = 0;

        return {
            arr: array,
            push: function (command) {
                index = array.length + 1;
                array.push(command);
                this.save();
                return;
            },
            next: function () {
                index = index >= array.length ? array.length : index + 1;
                return array[index] || "";
            },
            prev: function () {
                index = index - 1 > 0 ? index - 1 : 0;
                return array[index] || "";
            },
            save: function () {
                if (window.localStorage) {
                    localStorage.setItem("ConsoleHistory", array);
                }
                return this;
            },
            load: function () {
                if (localStorage && localStorage.getItem("ConsoleHistory")) {
                    array = localStorage.getItem("ConsoleHistory").split(",");
                    index = array.length;
                } else {
                    array = ["?"];
                    index = 0;
                }
                return this;
            }
        };
    };

    Console = function (el, socketUrl) {

        el = $(el);

        var console = {

            el: el,

            socket: new window.WebSocket(socketUrl),

            history: (new History()).load(),

            write: function (msg) {
                msg = msg === "" ? invite : msg + "\n" + invite;
                el.val(el.val() + "\n" + msg);
                setTimeout(function () { el[0].scrollTop = el[0].scrollHeight; }, 0);
            },

            lastCommand: function () {
                return this.history[this.history.length - 1];
            },

            fill: function (command) {
                var lines = el.val().split("\n");
                lines[lines.length - 1] = invite + command;
                el.val(lines.join("\n"));
            },

            execute: function (command) {
                logger.info("Executing command: " + command);
                el.attr("disabled", "disabled");
                this.socket.send(command);
                this.history.push(command);
            },

            clear: function () {
                el.val(invite);
            },

            clearHistory: function () {
                this.history = [];
            },

            newLine: function () {
                this.write("");
            }

        };

        console.socket.onmessage = function (event) {
            if (event.data === "ping") { return; }
            logger.info("Received data: " + event.data);
            console.write(event.data);
            console.el.removeAttr("disabled");
            console.el.focus();
            console.el[0].setSelectionRange(console.el[0].value.length, console.el[0].value.length);
        };

        console.socket.onclose = function (event) {
            logger.info("[Socket closed]");
            console.fill("[Socket closed]");
            console.el.attr("disabled", "disabled");
        };

        function parseCommands() {
            var lines = el.val().split(invite),
                last = lines[lines.length - 1];

            return last.split("\n");

            /*logger.info("val: " + el.val());
             logger.info("lines: ");
             logger.info(lines[lines.length - 1]);
             return last ? last.match(new RegExp(invite + ".+")) ? last.replace(invite, "").trim() : undefined : undefined;*/
        }

        function getLastLine() {
            var lines = el.val().split("\n");
            return lines[lines.length - 1];
        }

        el.keyup(function (event) {

            // Backspace + Delete
            if (event.which === 8 || event.which === 46) {
                var last = getLastLine();
                if (!last || last === "" || invite.startsWith(last)) {
                    console.fill("");
                    return false;
                }
            }

        }).keydown(function (event) {

            //window.console.log(event.which);

            var last, commands;

            // Backspace || Delete
            if (event.which === 8 || event.which === 46) {
                last = getLastLine();
                window.console.log(last);
                if (!last || last === "" || last === invite) {
                    console.fill("");
                    return false;
                }
            }

            // Enter
            if (event.keyCode === 13) {
                commands = parseCommands();
                _.each(commands, function (command) {
                    if (command) {
                        console.execute(command);
                    } else {
                        console.newLine();
                    }
                });
                return false;
            }

            // Ctrl + l
            if (event.keyCode === 76 && event.ctrlKey) {
                console.clear();
                return false;
            }

            // Up arrow
            if (event.keyCode === 38) {
                console.fill(console.history.prev());
                return false;
            }

            // Down arrow
            if (event.keyCode === 40) {
                console.fill(console.history.next());
                return false;
            }
        });

        el.focus();
        console.fill("");

        return console;
    };

    return Console;

});