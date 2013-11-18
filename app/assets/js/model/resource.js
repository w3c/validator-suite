define(["model/model", "model/assertions", "util/Util", "util/Logger"], function (Model, Assertions, Util, Logger) {

    "use strict";

    var Resource = Model.extend({

        logger: Logger.of("Resource"),

        defaults: {
            url: "",
            lastValidated: null,
            warnings: 0,
            errors: 0
        },

        assertions: new Assertions(),

        init: function () {
            //this.id = this.get("resourceUrl");
        },

        // TODO Use play js utility for urls!

        url: function () {
            return "resources?resource=" + encodeURIComponent(this.get("url"));
        },

        reportUrl: function () {
            return "assertions?resource=" + encodeURIComponent(this.get("url"));
        },

        search: function (search) {
            return this.get("url").toLowerCase().indexOf(search.toLowerCase()) > -1;
        }

        /*validate: function (attrs) {
         if (!attrs.resourceUrl || attrs.resourceUrl.length < 1) {
         logger.warn("Resource url required");
         return "Resource url required";
         }
         },*/

    });

    Resource.View = Resource.View.extend({

        templateId: "resource-template",

        events: {
            "click .print": "print",
            "click .foldAllToggle": "foldAllToggle"
        },

        print: function () {
            if (this.model.collection.options.assertions) {
                this.model.collection.options.assertions.view.render({dump: true});
            }
            window.print();
            return false;
        },

        templateOptions: function () {
            return {
                reportUrl: this.model.reportUrl()
            };
        },

        addSearchHandler: function () {
            var collec = this.options.assertions, input;
            if (!collec) {
                return;
            }
            this.$(".actions input[name=search]").bind("keyup change", function () {
                input = this;
                setTimeout(function () {
                    collec.view.search(input.value, input);
                }, 0);
            });
        },

        afterRender: function () {
            this.addSearchHandler();
        },

        unfoldAll: function () {
            if (this.model.collection.options.assertions) {
                this.model.collection.options.assertions.view.unfoldAll();
            }
        },

        foldAll: function () {
            if (this.model.collection.options.assertions) {
                this.model.collection.options.assertions.view.foldAll();
            }
        },

        foldAllToggle: function (event) {
            this.$el.toggleClass("foldAll");
            if (this.model.collection.options.assertions) {
                if (this.$el.hasClass("foldAll")) {
                    this.unfoldAll();
                    $(event.target).text("Collapse all");
                } else {
                    this.foldAll();
                    $(event.target).text("Expand all");
                }
            }
        },

    });

    Resource.fromHtml = function ($article) {
        var value = Util.valueFrom($article);
        return {
            id: $article.attr("data-id"),
            url: value('data-url'),
            lastValidated: {
                timestamp: value('data-lastValidated'),
                legend1: value('data-lastValidated-legend1'),
                legend2: value('data-lastValidated-legend2')
            },
            warnings: parseInt(value('data-warnings'), 10),
            errors: parseInt(value('data-errors'), 10)
        };
    };

    return Resource;

});