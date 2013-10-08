require([
    "libs/jquery",
    "libs/foundation",
    "libs/foundation.reveal",
    "libs/foundation.orbit",
    "libs/foundation.dropdown"], function ($) {

    "use strict";

    $(document).foundation();

    $(".range").each(function (i, element) {
        var output, input = $("input", element);
        if (input[0] && input[0].type === "range") {
            output = $("<input type='text'></input>");
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
