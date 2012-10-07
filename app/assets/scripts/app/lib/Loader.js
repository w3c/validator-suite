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

        var params = this.params = _.extend(Loader.defaultParams, (initial || {})),
            self = this;

        params.success = function (all, models) {
            logger.info("request complete");

            if (models.length == 0) {
                if (params.data["search"] && params.data["search"] != "") {
                    logger.info("no more search results. proceeding without search param");
                    params.data.search = "";
                    params.data.offset = 0 - params.data.n;
                } else {
                    logger.warn("empty result. collection size: " + collection.size() + "/" + collection.expected);
                    return;
                }
            }
            collection.trigger('reset');

            setTimeout(function () {
                if (!collection.isComplete()) {
                    params.data.offset = params.data.offset + params.data.n;
                    self.xhr = collection.fetch(params);
                    logger.info("sent next request with offset: " + params.data.offset);
                } else {
                    logger.info("collection complete");
                }
            }, 0);
        };

        /*params.error = function (models) {
            logger.error("http error, retrying");
            self.xhr = collection.fetch(params);
        };*/

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

        start: function () {
            if (this.xhr) { this.stop(); }
            this.xhr = this.collection.fetch(this.params);
            logger.info("loader started with params:");
            logger.debug(this.params);
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
            var n = this.params.data.n;
            data = _.extend({ offset: -n, n: n }, data);
            this.params.data = data;
            logger.info("data changed");
            logger.debug(data);
            return data;
        }

    });

    return Loader;

});