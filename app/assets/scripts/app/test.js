require([
    "lib/Util",
    "lib/Logger",
    "lib/Socket",
    "model/model",
    "model/job",
    "model/resource",
    "model/assertor",
    "model/assertion",
    "collection/jobs",
    "collection/resources",
    "collection/assertors",
    "collection/assertions"],
    function (
        Util,
        Logger,
        Socket,
        Model,
        Job,
        Resource,
        Assertor,
        Assertion,
        Jobs,
        Resources,
        Assertors,
        Assertions
    ) {

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
                root.assertions = new Assertions().configure({
                    el: el
                });
            });

            $("#resources").map(function (i, el) {
                root.resources = new Resources().configure({
                    el: el,
                    assertions: root.assertions
                });
            });

            $("#assertors").map(function (i, el) {
                root.assertors = new Assertors().configure({
                    el: el,
                    assertions: root.assertions
                });
            });

            $("#jobs").map(function (i, el) {

                root.jobs = new Jobs().configure({
                    el: el,
                    searchInput: $("#actions input[name=search]"),
                    assertions: root.assertions,
                    resources: root.resources,
                    loadFromMarkup: true // must stay true
                });

            });



        });
    });