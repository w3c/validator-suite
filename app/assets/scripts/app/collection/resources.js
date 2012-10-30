define(["lib/Logger", "lib/Util", "lib/Socket", "model/resource", "lib/Loader", "libs/backbone", "collection/collection"], function (Logger, Util, Socket, Resource, Loader, Backbone, Collection) {

    "use strict";

    var logger = new Logger("Resources"),
        Resources;

    Resources = Collection.extend({

        model: Resource

    });

    Resources.View = Resources.View.extend({

        templateId: "resource-template",

        attributes: {
            id: "resources"
        },

        sortParams: [
            "url",
            "validated",
            "errors",
            "warnings"
        ],

        /*search: function (search) {
            this.search_ = function (resource) {
                return resource.get("resourceUrl").toLowerCase().indexOf(search.toLowerCase()) > -1;
            };
            this.render();
        },*/

        init: function () {
            if (!this.isList()) {
                var view = this.collection.at(0).view();
                view.options.assertions = this.options.assertions;
                view.addSearchHandler();
            }
        },

        emptyMessage: "No resources to show." // resources.empty

    });

    return Resources;

});