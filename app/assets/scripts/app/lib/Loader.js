define(["w3"], function (W3) {

    var logger = new W3.Logger("Loader");

    /**
     * var loader = new Loader(collection);
     * loader.start();
     * loader.setData({ search: "w3c" });
     * loader.stop();
     *
     * loader.params
     * loader.xhr
     *
     * @param collection
     * @param initial
     * @constructor
     */
    var Loader = function (collection, initial) {

        if (!collection || !collection.expected)
            W3.exception("collection not provided or does not have an expected count");

        this.collection = collection;

    };

    Loader.defaultParams = {
        silent: true,
        add: true,
        cache: false,
        data: {
            offset: 0,
            n: 50
        }
    };

    _.extend(Loader.prototype, {

        start: function (initial) {
            if (this.xhr) { this.stop(); }

            var params = this.params = _.extend({}, Loader.defaultParams, (initial || {})),
                self = this;

            // make sure we have offset and n
            params.data = _.extend({}, Loader.defaultParams.data, this.params.data);

            console.log(Loader.defaultParams.data.offset);
            console.log(params.data.offset);

            params.success = function (all, models) {
                logger.info("request complete");

                if (models.length == 0) {
                    if (params.data["search"] && params.data["search"] != "") {
                        logger.info("no more search results. proceeding without search param");
                        params.data.search = "";
                        params.data.offset = 0 - params.data.n;
                    } else {
                        logger.warn("empty result. collection size: " + self.collection.size() + "/" + self.collection.expected);
                        return;
                    }
                }

                self.collection.trigger('reset'); // trigger own event

                setTimeout(function () {
                    if (!self.collection.isComplete()) {

                        if (!_.isNumber(params.data.n) || !_.isNumber(params.data.offset)) {
                            W3.exception("offset or n is not a number");
                        }
                        params.data.offset = params.data.offset + params.data.n;
                        self.xhr = self.collection.fetch(params);
                        logger.info("sent next request with offset: " + params.data.offset);
                    } else {
                        logger.info("collection complete");
                    }
                }, 0);
            };

            logger.info("loader started with params:");
            logger.debug(params.data);
            this.xhr = this.collection.fetch(this.params);
            return this.xhr;
        },

        stop: function () {
            if (this.xhr) {
                this.xhr.abort();
                delete this.xhr;
                logger.info("loader stoped");
                return true;
            }
            logger.info("loader already stopped");
            return false;
        },

        setData: function (data) {
            this.params.data = _.extend({}, Loader.defaultParams.data, data);
            logger.info("data set to:");
            console.log(this.params.data);


            if (this.collection.isComplete()) return false;

            var params = this.params;
            delete params.success;
            return this.collection.fetch(params); // next loader request will be for offset + n
        }

    });

    return Loader;

});