require(["model/vs"], function (VS) {

    "use strict";

    if (_.isUndefined(document.createElement('progress').position)) {
        require(["libs/progress-polyfill"]);
    }

    $(function () {

        // TODO
        $("#actions .clear").remove();
        $("#actions .search input").removeClass("clearable");

        var root = window;

        //var socket = new Socket(location);

        $("#assertions").map(function (i, el) {
            root.assertions = new VS.Assertions().configure({
                el: el
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

    });
});