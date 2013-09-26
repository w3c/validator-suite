require([
    "libs/jquery",
    "libs/foundation",
    "libs/foundation.reveal",
    "libs/foundation.orbit",
    "libs/foundation.dropdown"], function ($) {

    "use strict";

    $(document).foundation();

    $(".range").each(function (i, element) {
        var span, input = $("input", element);
        if (input[0] && input[0].type === "range") {
            span = $("<span></span>");
            input.after(span);
            span.text(input[0].value);
            input.on("change", function (e) {
                span.text(e.target.value);
            });
        }
    });
});
