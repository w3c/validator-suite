define(["util/Logger", "model/resource", "model/collection", "util/Socket"], function (Logger, Resource, Collection, Socket) {

    "use strict";

    var Resources = Collection.extend({

        logger: Logger.of("Resources"),

        model: Resource

    });

    Resources.View = Resources.View.extend({

        attributes: {
            id: "resources"
        },

        sortParams: [
            "url",
            "lastValidated",
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

        afterRender: function () {
            //console.log("Resources rendered");
        },

        emptyMessage: "No resources to show." // resources.empty

    });

    return Resources;

});