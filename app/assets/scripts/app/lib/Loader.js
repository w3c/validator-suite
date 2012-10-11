define(["w3", "libs/backbone"], function (W3, Backbone) {

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
    var Loader = function (collection, options) {

        this.options = _.extend({ silent: true, add: true, cache: false }, options);

        if (!collection || !collection.expected)
            W3.exception("collection not provided or does not have an expected count");

        this.collection = collection;

        logger.info("Created loader with options: " + JSON.stringify(options));

    };

    Loader.perPage = 2;

    _.extend(Loader.prototype, Backbone.Events, {

        start: function (data) {
            if (this.xhr) { this.stop(); }

            var params = _.clone(this.options);
            params.data = _.extend({ offset: 0, n: Loader.perPage }, data);

            logger.info("Started with initial data: " + JSON.stringify(params.data));

            this.xhr = this.request(params);
            return this.xhr;
        },

        request: function (params, force) {

            force = (force || false);

            if (this.xhr && this.xhr.readyState !== 4 && this.xhr.readyState !== 0) {
                if (!force) {
                    this.xhr.nextData = params.data;
                    logger.info("Set data for next call: " + JSON.stringify(params.data));
                    return this.xhr;
                }
                this.xhr.abort();
                logger.info("Previous request aborted");
            }

            logger.info("New request: " + JSON.stringify(params.data));
            params.success = this.getSuccess(params);
            this.xhr = this.collection.fetch(params);
            this.xhr.params = params;
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

        setData: function (data, force) {
            if (this.collection.isComplete()) return false;

            var params = _.clone(this.options);
            params.data = _.extend({ offset: 0, n: Loader.perPage }, data);

            return this.request(params, force);
        },

        getSuccess: function (params) {

            if (!_.isNumber(params.data.n) || !_.isNumber(params.data.offset)) {
                W3.exception("offset or n is not a number");
            }

            var self = this;

            return function (collection, models) {

                if (!self.xhr.nextData) {

                    if (models.length < params.data.n && _.isString(params.data["search"])
                            && params.data["search"] !== "") {
                        logger.info("No more search results. Proceeding without search param.");
                        params.data.search = "";
                        params.data.offset = 0;
                        self.setData(params.data);
                        return;
                    }

                    if (models.length == 0 && (!params.data["search"] || params.data["search"] == "")) {
                        logger.error("empty result. collection size: " + self.collection.size() + "/" + self.collection.expected);
                        self.stop();
                        return;
                    }

                }

                setTimeout(function () {

                    self.trigger('update');

                    if (!self.collection.isComplete()) {
                        if (self.xhr.nextData) {

                            // if only offset differs use the previous one, review
                            var diff = false;
                            for (v in self.xhr.nextData) {
                                if (v !== "offset" && self.xhr.nextData[v] != params.data[v]) {
                                    diff = true;
                                    break;
                                }
                            }

                            if (diff) {
                                params.data = self.xhr.nextData;
                            } else {
                                logger.info("keeping previous data");
                                params.data.offset = params.data.offset + params.data.n;
                            }

                        } else {
                            params.data.offset = params.data.offset + params.data.n;
                        }
                        _.bind(self.request, self)(params);
                    } else {
                        logger.info("collection complete");
                        self.stop();
                    }
                }, 0);
            };
        },

        isSearching: function () {
            return this.xhr && _.isString(this.xhr.params.data.search)
                    &&this.xhr.params.data.search !== "" ? true : false;
        },


        isLoading: function () {
            return this.xhr ? true : false;
        }


    });

    return Loader;

});