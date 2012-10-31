define(["lib/Logger", "model/resource", "collection/collection"], function (Logger, Resource, Collection) {

    "use strict";

    var logger = new Logger("Resources"),
        Resources;

    Resources = Collection.extend({

        model: Resource

    });

    Resources.View = Resources.View.extend({

        attributes: {
            id: "resources"
        },

        sortParams: [
            "url",
            "validated",
            "errors",
            "warnings"
        ],

        init: function () {
            if (!this.isList()) {
                var view = this.collection.at(0).view;
                view.options.assertions = this.options.assertions;
                view.addSearchHandler();
            }
        },

        emptyMessage: "No resources to show." // resources.empty

    });

    return Resources;

});