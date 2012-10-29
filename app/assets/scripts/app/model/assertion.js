define(["lib/Logger", "lib/Util", "model/model"], function (Logger, Util, Model) {

    "use strict";

    var logger = new Logger("Assertion"),
        Assertion;

    Assertion = Model.extend({

        defaults: {
            assertor: "none",
            severity: "info",
            title: "",
            description: null,
            occurrences: 0,
            occurrencesLegend: "",
            contexts: [],
            resources: []
        },

        search: function (search) {
            return this.get("title").toLowerCase().indexOf(search.toLowerCase()) > -1;
        }

    });

    Assertion.View = Assertion.View.extend({

        templateId: "assertion-template",

        init: function () {
            this.el.setAttribute("data-id", this.model.id);
        },

        events: {
            "click .title": "fold"
        },

        fold: function (event) {
            $(event.currentTarget).parents("article").toggleClass("folded");
        },

        isFoldable: function () {
            /*console.log("is null?")
            console.log(!_.isNull(this.model.get("description")));
            console.log("has contexts?")
            console.log(this.model.get("contexts").length > 0);
            console.log("has resources?")
            console.log(this.model.get("resources").length > 0);*/
            return !_.isNull(this.model.get("description")) ||
                    this.model.get("contexts").length > 0 ||
                    this.model.get("resources").length > 0;
        }

    });

    Assertion.fromHtml = function (value, $elem) {
        return {
            id: $elem.attr("data-id"),
            assertor: value('data-assertor'),
            severity: value('data-severity'),
            occurrences: value('data-occurrences'),
            occurrencesLegend: value('data-occurrencesLegend'),
            title: value('data-title'),
            description: value('data-description')
        };
    };

    return Assertion;

});