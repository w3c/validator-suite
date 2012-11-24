define(["lib/Logger", "model/resource", "collection/collection", "lib/Socket"], function (Logger, Resource, Collection, Socket) {

    "use strict";

    var logger = new Logger("Resources"),
        Resources;

    Resources = Collection.extend({

        model: Resource,

        listen: function () {
            //var socket = new Util.Socket(this.url);
            var self = this;

            this.socket = new Socket(this.url);
            self.socket.on("message", function (data) {
                _.each(data, function (data) {
                    logger.debug(data);
                    var model = self.get(data.resourceUrl);
                    if (!_.isUndefined(model)) {
                        model.set(data);
                    } else {
                        self.add(new Resource(data));
                        logger.warn("Unknown model with resourceUrl: " + data.resourceUrl);
                        logger.debug(data);
                    }
                });
            });
        }

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