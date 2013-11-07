define(["util/Logger", "model/job", "model/collection"], function (Logger, Job, Collection) {

    "use strict";

    var Jobs = Collection.extend({

        logger: Logger.of("Jobs"),

        model: Job

    });

    Jobs.View = Jobs.View.extend({

        attributes: {
            id: "jobs"
        },

        sortParams: [
            "name",
            "entrypoint",
            "status",
            "completedOn",
            "errors",
            "warnings",
            "resources",
            "maxResources",
            "health"
        ],

        init: function () {
            var self = this, view, input;
            if (!this.isList()) {
                view = this.collection.at(0).view;
                view.options.assertions = this.options.assertions;
                view.options.resources = this.options.resources;
                view.options.softRender = false;
                view.addSearchHandler();
            }
            $("#actions input[name=search]").bind("keyup change", function () {
                input = this;
                setTimeout(function () {
                    self.search(input.value, input);
                }, 0);
            });
        },

        emptyMessage: function () {
            return "No jobs have been configured yet. <a href='" + this.collection.url + "/new" + "'>Create your first job.</a>";
        }

    });

    return Jobs;

});