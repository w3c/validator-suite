require([
    "libs/jquery",
    "libs/foundation",
    "libs/foundation.reveal",
    "libs/foundation.orbit",
    "libs/foundation.dropdown"], function ($) {

    "use strict";

    $(document).foundation();
    $(document).foundation('reveal', {
        opened: function (event) {
            var modal = event.target;
            $("h1", modal).focus();
        }
    });

    $("input[type=range]").each(function (i, element) {
        var output, input = $(element);
        if (input[0] && input[0].type === "range") {
            output = $("<input class='rangeOutput' type='text'></input>");
            input.after(output);
            output.val(input[0].value);
            input.on("change", function (e) {
                output.val(e.target.value);
            });
            output.on("change", function (e) {
                input.val(e.target.value);
            });
        }
    });
});
