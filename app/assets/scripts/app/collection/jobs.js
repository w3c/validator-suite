define(["lib/Logger", "lib/Util", "lib/Socket", "model/job", "lib/Loader", "libs/backbone", "collection/collection"], function (Logger, Util, Socket, Job, Loader, Backbone, Collection) {

    "use strict";

    var logger = new Logger("Jobs"),
        Jobs;

    Jobs = Collection.extend({

        model: Job

    });

    Jobs.View = Jobs.View.extend({

        templateId: "job-template",

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
            var self = this, view, value;
            if (!this.isList()) {
                view = this.collection.at(0).view();
                view.options.assertions = this.options.assertions;
                view.options.resources = this.options.resources;
                view.addSearchHandler();
            }
            $("#actions input[name=search]").bind("keyup change", function () {
                value = this.value;
                setTimeout(function () {
                    self.search(value);
                }, 0);
            });
        },

        emptyMessage: function () {
            return "No jobs have been configured yet. <a href='" + this.collection.url + "/new" + "'>Create your first job.</a>";
        }

    });

    return Jobs;

});