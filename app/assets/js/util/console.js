define(["util/Socket", "libs/jquery"], function (Socket, $) {

    "use strict";

    var invite = "> ",
        History,
        Console;

    History = function () {

        var array = [],
            index = 0;

        return {
            arr: array,
            push: function (command) {
                index = array.length + 1;
                return array.push(command);
            },
            next: function () {
                index = index >= array.length ? array.length : index + 1;
                return array[index] || "";
            },
            prev: function () {
                index = index - 1 > 0 ? index - 1 : 0;
                return array[index] || "";
            }
        };
    };

    Console = function (el, socketUrl) {

        el = $(el);

        var console = {

            el: el,

            socket: new window.WebSocket(socketUrl),

            history: new History(),

            write: function (msg) {
                msg = msg === "" ? invite : msg + "\n" + invite;
                el.val(el.val() + "\n" + msg);
                el.scrollTop(el[0].scrollHeight);
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
            console.write(event.data);
            console.el.removeAttr("disabled");
        };

        console.socket.onclose = function (event) {
            console.fill("[Socket closed]");
            el.attr("disabled", "disabled");
        };

        function parseCommand() {
            var lines = el.val().split("\n"),
                last = lines[lines.length - 1];
            return last ? last.match(new RegExp(invite + ".+")) ? last.replace(invite, "").trim() : undefined : undefined;
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

            var last, command;

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
                command = parseCommand();
                if (command) {
                    console.execute(command);
                } else {
                    console.newLine();
                }
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
        console.fill("?");

        return console;
    };

    return Console;

});