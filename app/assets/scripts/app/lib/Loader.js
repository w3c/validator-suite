define(["lib/Logger", "lib/Util", "libs/backbone"], function (Logger, Util, Backbone) {

    "use strict";

    var logger = new Logger("Loader");

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

        if (_.isUndefined(collection) || !_.isNumber(collection.expected)) {
            Util.exception("No collection was provided or it does not have a valid expected count");
        }

        this.collection = collection;

        logger.info("Created loader with options: " + JSON.stringify(this.options));

    };

    Loader.perPage = 30;

    _.extend(Loader.prototype, Backbone.Events, {

        start: function (data) {

            //if (this.collection.isComplete()) { return false; }
            if (this.collection.expected === 0) { return false; }

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
                this.trigger('stopped');
                logger.info("loader stoped");
                return true;
            }
            logger.info("loader already stopped");
            return false;
        },

        setData: function (data, force) {
            if (this.collection.isComplete()) { return false; }

            var params = _.clone(this.options);
            params.data = _.extend({}, this.xhr.params.data || {}, { offset: 0, n: Loader.perPage }, data);

            return this.request(params, force);
        },

        getSuccess: function (params) {

            if (!_.isNumber(params.data.n) || !_.isNumber(params.data.offset)) {
                Util.exception("offset or n is not a number");
            }

            var self = this;

            return function (collection, models) {

                self.collection.trigger('reset');

                if (!self.xhr.nextData) {

                    if (models.length < params.data.n && _.isString(params.data["search"])
                            && params.data["search"] !== "") {
                        logger.info("No more search results. Proceeding without search param.");
                        self.trigger('stopSearching');
                        params.data.search = "";
                        params.data.offset = 0;
                        self.setData(params.data);
                        return;
                    }

                    if (models.length === 0 && (!params.data["search"] || params.data["search"] == "")) {
                        logger.error("empty result. collection size: " + self.collection.size() + "/" + self.collection.expected);
                        self.stop();
                        return;
                    }

                }

                setTimeout(function () {

                    self.trigger('update');

                    if (!self.collection.isComplete()) {
                        if (self.xhr.nextData) {

                            // if only offset differs use the previous one
                            var diff = false,
                                previous = _.clone(params.data),
                                next = _.clone(self.xhr.nextData);

                            delete previous.offset;
                            delete next.offset;

                            if (!_.isEqual(previous, next)) {
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
            var a = this.xhr && _.isString(this.xhr.params.data.search)
                    && this.xhr.params.data.search !== "",
                b = this.xhr && this.xhr.nextData && _.isString(this.xhr.nextData.search)
                    && this.xhr.nextData.search !== "";
            return (a || b);
        },


        isLoading: function () {
            return this.xhr ? true : false;
        }

    });

    return Loader;

});