require([
    "lib/Util",
    "lib/Loader",
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
        Loader,
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

        $(function () {

            window.model = Model;

            $("#actions .clear").remove();
            $("#actions .search button").remove();
            $("#actions .search input").removeClass("clearable");
            $('nav.pagination :not(p.legend)').remove();
            $('body > footer').addClass('jsFixed');
            // TODO height + padding-top + padding-bottom
            //$('#main').css("padding-bottom", footer.height() + "px");
            $('#main').css("padding-bottom", "50px");

            var root = window;

            //var socket = new Socket(location);

            $("#assertions").map(function (i, el) {
                root.assertions = new Assertions().configure({
                    el: el,
                    //loadFromMarkup: false
                });
            });

            $("#resources").map(function (i, el) {
                root.resources = new Resources().configure({
                    el: el,
                    assertions: root.assertions,
                    //loadFromMarkup: false
                });
            });

            $("#assertors").map(function (i, el) {
                root.assertors = new Assertors().configure({
                    el: el,
                    assertions: root.assertions,
                    //loadFromMarkup: false
                });
            });

            $("#jobs").map(function (i, el) {

                //var url = $(el).attr("data-url");

                root.jobs = new Jobs().configure({
                    el: el,
                    searchInput: $("#actions input[name=search]"),
                    assertions: root.assertions,
                    resources: root.resources,
                    //loadFromMarkup: false
                    //load: false
                });

            });

        });
    });