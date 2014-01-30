require([
    "libs/underscore",
    "libs/jquery",
    "model/vs",
    "config",
    "libs/foundation.dropdown",
    "libs/foundation.reveal"], function (_, $, VS, config) {

    "use strict";

    $(document).foundation('dropdown reveal', {
        animation: 'fade',
        animationSpeed: 100,
        opened: function (event) {
            var modal = event.target;
            $(".button.no", modal).focus();
        }
    });

    if (_.isUndefined(document.createElement('progress').position)) {
        require(["libs/progress-polyfill"]);
    }

    $(function () {

        // TODO
        $("#actions .clear").remove();
        $("#actions .search input").removeClass("clearable");
        $("#actions .searchForm").on("submit", function () {return false;});

        var root = window;

        //var socket = new Socket(location);

        $("#assertions").map(function (i, el) {
            root.assertions = new VS.Assertions().configure({
                el: el,
                listen: true
            });
        });

        $("#resources").map(function (i, el) {
            root.resources = new VS.Resources().configure({
                el: el,
                assertions: root.assertions
            });
        });

        $("#assertors").map(function (i, el) {
            root.assertors = new VS.Assertors().configure({
                el: el,
                assertions: root.assertions
            });
        });

        $("#jobs").map(function (i, el) {

            root.jobs = new VS.Jobs().configure({
                el: el,
                searchInput: $("#actions input[name=search]"),
                assertions: root.assertions,
                resources: root.resources,
                loadFromMarkup: true // must stay true
            });

        });

        window.VS = VS;

    });
});