define(["lib/Logger", "model/assertor", "lib/Loader", "lib/Util", "lib/Socket", "libs/backbone", "collection/Collection"], function (Logger, Assertor, Loader, Util, Socket, Backbone, Collection) {

    "use strict";

    var logger = new Logger("Assertors"),
        Assertors;

    Assertors = Collection.extend({

        model: Assertor

    });

    Assertors.View = Assertors.View.extend({

        templateId: "assertor-template",

        attributes: {
            id: "assertors"
        },

        getSortParam: function () {
            return {
                param: "errors",
                reverse: true
                //string: "errors"
            };
        },

        afterRender: function () {
            this.addFilterHandler();
            if (this.$('.current').size() === 0) {
                this.$("article:first-of-type .filter").click();
            }
        },

        addFilterHandler: function () {
            var filterLinks = this.$(".filter"),
                self = this;
            filterLinks.unbind('click');
            filterLinks.click(function (event) {
                event.preventDefault();
                filterLinks.parents('article').removeClass("current");
                $(this).parents('article').addClass("current");
                self.options.assertions.view().filterOn($(this).parents('article').attr('data-id'));
                return false;
            });
        }

    });

    return Assertors;

    Assertors.Collection = Collection.extend({

        model: Assertor

    });

    Assertors.View = Backbone.View.extend({

        tagName: "section",

        attributes: {
            id: "assertors"
        },

        events: {

        },

        collection: new Assertors.Collection(),

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
                //try {
                    collection.reset(this.$('article').map(function (i, article) {
                        console.log(article);
                        var a = Assertor.fromHtml(article);
                        console.log(a);
                        return a;
                    }).toArray());
//                } catch (ex) {
//                    logger.error(ex);
//                }
            }

            console.log(collection.length)

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

            var elements = models.map(function (assertor, index) {
                //var view = assertor.view();

                //console.log(assertor.view());

                return assertor.view().render().el;
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

            /*var views = _.pluck(this.displayed, 'view');
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
            }*/

        }

    });

    return Assertors;

});