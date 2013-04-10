require([
    "lib/Util",
    "lib/Logger",
    "lib/Socket"],
    function (
        Util,
        Logger,
        Socket
    ) {

        "use strict";

        var isCtrl = false,
            invite = "> ",
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

            var parseCommand = function () {
                var lines = el.val().split("\n"),
                    last = lines[lines.length - 1];
                return last ? last.match(new RegExp(invite + ".+")) ? last.replace(invite, "").trim() : undefined : undefined;
            };

            var getLastLine = function () {
                var lines = el.val().split("\n");
                return lines[lines.length - 1];
            };

            el.keyup(function (event) {
                // Ctrl
                if (event.which === 17) { isCtrl = false; }

                // Backspace + Delete
                if (event.which === 8 || event.which === 46) {
                    var last = getLastLine();
                    if (!last || last === "" || invite.startsWith(last)) {
                        console.fill("");
                        return false;
                    }
                }

            }).keydown(function (event) {
                // Ctrl
                if (event.which === 17) { isCtrl = true; }

                //window.console.log(event.which);

                // Backspace + Delete
                if (event.which === 8 || event.which === 46) {
                    var last = getLastLine();
                    window.console.log(last);
                    if (!last || last === "" || last === invite) {
                        console.fill("");
                        return false;
                    }
                }

                // Enter
                if (event.keyCode === 13) {
                    var command = parseCommand();
                    if (command) {
                        console.execute(command);
                    } else {
                        console.newLine();
                    }
                    return false;
                }

                // Ctrl + l
                if (event.keyCode === 76 && isCtrl === true) {
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

        $(function () {
            var el = $(".console");
            var url = Util.resolveUrl(el.attr("data-socket"))
                .replace("http://", "ws://")
                .replace("https://", "wss://");
            window.Console = new Console(el, url);
        });

    });