define(["lib/Logger", "model/assertion", "lib/Loader", "lib/Util", "lib/Socket", "libs/backbone", "collection/Collection"], function (Logger, Assertion, Loader, Util, Socket, Backbone, Collection) {

    "use strict";

    var logger = new Logger("Assertions"),
        Assertions;

    Assertions = Collection.extend({

        model: Assertion,

        comparator: function (o1, o2) {
            if (o1.get("severity") === o2.get("severity")) {
                return o1.get("occurrences") === o2.get("occurrences") ?
                        (o1.get("title") > o2.get("title") ? +1 : -1) :
                        (o1.get("occurrences") > o2.get("occurrences") ? -1 : +1);
            }
            if (o1.get("severity") === "error") {
                return -1;
            }
            if (o2.get("severity") === "error") {
                return +1;
            }
            if (o1.get("severity") === "warning" && o2.get("severity") === "info") {
                return -1;
            }
            if (o1.get("severity") === "info" && o2.get("severity") === "warning") {
                return +1;
            }
        }

    });

    Assertions.View = Assertions.View.extend({

        attributes: {
            id: "assertions"
        },

        sortParams: [],

        filterOn: function (assertorId) {
            this.filter = function (assertion) {
                return assertion.get("assertor") === assertorId;
            };
            this.render();
        }

        /*search: function (search) {
            this.search_ = function (resource) {
                return resource.get("title").toLowerCase().indexOf(search.toLowerCase()) > -1;
            };
            this.render();
        }*/

    });

    return Assertions;

/*    Assertions.getComparatorBy = function (param, reverse) {

        reverse = (reverse || false);

        if (param == "completedOn") {
            var co = "completedOn";
            return function (o1, o2) {
                var p1 = o1.get(co);
                var p2 = o2.get(co);
                if (p1 == null && p2 == null) {
                    return 0;
                } else if (p2 == null) {
                    return reverse ? -1 : +1;
                } else if (p1 == null) {
                    return reverse ? +1 : -1;
                } else if (p1.timestamp > p2.timestamp) {
                    return reverse ? -1 : +1;
                } else if (p1.timestamp == p2.timestamp) {
                    return 0;
                } else {
                    return reverse ? +1 : -1;
                }
            };
        }

        return function (o1, o2) {
            if (o1.get(param) > o2.get(param) || (o1.get(param) === o2.get(param) && o1.id > o2.id)) {
                return reverse ? -1 : +1;
            } else if (o1.get(param) === o2.get(param) && o1.id === o2.id) {
                return 0;
            } else {
                return reverse ? +1 : -1;
            }
        };
    }; */

    Assertions.Collection = Collection.extend({

        model: Assertion

        /*comparator: Assertions.getComparatorBy("name"),

        sortByParam: function (param, reverse) {
            this.comparator = Assertions.getComparatorBy(param, reverse);
            this.sort();
        },

        isComplete: function () {
            return this.expected && this.length < this.expected ? false : true;
        },

        initialize: function () {
            this.on('add reset', function () {
                if (this.expected && this.expected < this.length) {
                    this.expected = this.length;
                }
            });
        }*/
    });

    Assertions.View = Backbone.View.extend({

        tagName: "section",

        attributes: {
            id: "assertions"
        },

        events: {

        },

        collection: new Assertions.Collection(),

        displayed: [],

        maxOnScreen: 30,

        filteredCount: 0,

        initialize: function () {

            var collection = this.collection,
                jobsSection = this.$el,
                self = this,
                sortParams = [
                    "name",
                    "entrypoint",
                    "status",
                    "completedOn",
                    "errors",
                    "warnings",
                    "resources",
                    "maxResources",
                    "health"
                ],
                sortLinks = this.$(".sort a"),
                win = $(window),
                aside = this.$('aside'),
                asideClone = aside.clone(),
                searchInput = this.searchInput = $("#actions input[name=search]");

            collection.expected = this.$el.attr('data-count');

//          Use the url parameter provided as an option or get it in the data-url attribute of this element.

            if (this.options.url) {
                collection.url = this.options.url;
            } else if (this.$el.attr('data-url')) {
                collection.url = this.$el.attr('data-url');
            } else {
                Util.exception('No url parameter was specified');
            }

//          Listen on collection events

            collection.on('add', this.render, this);
            collection.on('reset', this.render, this);
            collection.on('destroy', this.render, this);

//          Parse the jobs already on the page if our collection is new

            if (collection.length == 0 && this.$('article').size() > 0) {
                try {
                    collection.reset(this.$('article').map(function (i, article) {
                        return Assertion.fromHtml(article);
                    }).toArray());
                } catch (ex) {
                    logger.error(ex);
                }
            }

//          Start the loader

            if (this.options.load) {
                var loader = this.loader = new Loader(collection);
                loader.start({ sort: this.getSortParam(), search: this.getSearchParam() });
                loader.on('update', _.bind(this.render, this));

                // debug
                window.loader = loader;
            }

//          Open a socket and listen on jobupdate events

            this.socket = new Socket(collection.url);
            this.socket.on("jobupdate", function (data) {
                var job = collection.get(data.id);
                if (!_.isUndefined(job)) {
                    job.set(data);
                } else {
                    logger.warn("unknown job with id: " + data.id);
                    logger.debug(data);
                }
            });


            var initial_sort = this.getSortParam2();
            if (initial_sort.param) collection.sortByParam(initial_sort.param, initial_sort.reverse);

//          Add sorting handlers

            _.each(sortParams, function (param) {
                this.$("." + param + " .ascend").click(function (event) {
                    event.preventDefault();
                    sortLinks.removeClass("current");
                    $(this).addClass("current");
                    loader.setData({ sort: "-" + param, search: self.getSearchParam() }, true);
                    collection.sortByParam(param);
                    return false;
                });
                this.$("." + param + " .descend").click(function (event) {
                    event.preventDefault();
                    sortLinks.removeClass("current");
                    $(this).addClass("current");
                    loader.setData({ sort: param, search: self.getSearchParam() }, true);
                    collection.sortByParam(param, true);
                    return false;
                });
            }, this);

//          Add search handler

            searchInput.bind("keyup change", function (event) {
                var input = this;
                setTimeout(function () {
                    loader.setData({ sort: self.getSortParam(), search: input.value });
                    self.render();
                }, 0);
            });

//          Add scroll handler

            //var asideClone = aside.clone();
            win.bind("scroll resize", function (event) {
                setTimeout(function () {
                    if (jobsSection.offset().top > win.scrollTop()) {
                        aside.removeClass('jsFixed');
                        asideClone.remove();
                    } else {
                        aside.before(asideClone);
                        aside.addClass('jsFixed');
                    }
                    self.updateLegend();
                }, 0);
            });
            win.scroll();

        },

        getSortParam: function () {
            var current = this.$(".sort .current");
            var param = current.parents("dt").attr("class");
            param = current.hasClass("ascend") ? "-" + param : param;
            return param;
        },

        getSortParam2: function () {
            var current = this.$(".sort .current");
            return {
                param: current.parents("dt").attr("class"),
                reverse: ! current.hasClass("ascend")
            };
        },

        getSearchParam: function () {
            return this.searchInput ? this.searchInput.val() : "";
        },

        render: function () {

            //this.logger.log("render");

//          Filter before rendering

            //this.collection.sort();

            var models = this.collection.models;

            /*if (_.isFunction(this.filter)) {
                models = _.filter(models, this.filter, this);
            } */

            var search = this.getSearchParam();
            if (_.isString(search) && search != "") {
                models = _.filter(models, function (job) {
                    return job.get("name").indexOf(search) > -1 || job.get("entrypoint").indexOf(search) > -1;
                });
            }

            this.filteredCount = models.length;

            models = this.displayed = models.slice(0, this.maxOnScreen);

            //this.displayedJobs = models;

//          Create job views and render

            var elements = models.map(function (assertion, index) {
                //assertion.view = new Assertion.View({ model: assertion });
                return assertion.view().render().el;
            }, this);

            this.$el.children('article').remove();

            this.$('.empty').remove();
            if (elements.length > 0) {
                this.$el.append(elements);
            } else {
                var empty = $('<p class="empty"></p>');
                if (this.getSearchParam() != "" && this.loader && !this.loader.isSearching()) {
                    empty.text("No search result.");
                } else if (this.collection.expected == 0) {
                    empty.html("No jobs have been configured yet. <a href='" + this.collection.url + "/new" + "'>Create your first job.</a>");
                } else {
                    empty.html("<span class='loader'></span>");
                }
                this.$el.append(empty);
            }

            this.updateLegend();
        },

        updateLegend: function () {

            var views = _.invoke(this.displayed, 'view');
            var visibles = { first: null, last: null };
            var i = 0;
            for (i; i < views.length; i++) {
                var isVisible = views[i].isVisible();
                if (visibles.first == null) {
                    visibles.first = isVisible ? (i + 1) : null;
                }
                visibles.last = isVisible ? (i + 1) : visibles.last;
                if (!isVisible && visibles.first != null && visibles.last != null) {
                    break;
                }
            }

            var o = this.maxOnScreen;
            this.maxOnScreen = visibles.last && visibles.last >= 30 ? visibles.last + 5 : 30;

            if (this.maxOnScreen != o) {
                this.render();
            }

            var total = this.getSearchParam() != "" ? this.filteredCount : this.collection.expected;

            var legend = $('.pagination .legend');

            if (visibles.first != null && visibles.last != null) {
                //legend.text("Viewing " + visibles.first + "-" + visibles.last +
                //    " of " + total + " results (" + this.collection.length + "/" + this.collection.expected + " - " + this.maxOnScreen + ")");
                legend.text("Viewing " + visibles.first + "-" + visibles.last + " of " + total + " results");
            } else {
                legend.text("");
            }

        }

    });

    return Assertions;

});