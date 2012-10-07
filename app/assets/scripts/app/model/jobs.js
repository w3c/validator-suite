define(["w3", "model/job", "lib/Loader", "libs/backbone"], function (W3, Job, Loader, Backbone) {

    "use strict";

    var Jobs = {};

    Jobs.getComparatorBy = function (param, reverse) {

        // TODO Job.dummyJob
        var job = new Job.Model({name: "job", entrypoint: "http://www.example.com", maxResources: 1, completedOn: {}});
        if (!_.isNumber(job.get(param)) && !_.isString(job.get(param)))
            throw new Error("Cannot sort by \"" + param + "\", not a string or a number. " + job.get(param));

        var reverse = reverse ? reverse : false;
        return function (o1, o2) {
            if (o1.get(param) > o2.get(param)) {
                return reverse ? -1 : +1;
            } else if (o1.get(param) === o2.get(param)) {
                return 0;
            } else {
                return reverse ? +1 : -1;
            }
        };
    };

    Jobs.Collection = Backbone.Collection.extend({

        model: Job.Model,

        comparator: Jobs.getComparatorBy("name"),

        sortByParam: function (param, reverse) {
            this.comparator = Jobs.getComparatorBy(param, reverse);
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
        }
    });

    Jobs.View = Backbone.View.extend({

        logger: new W3.Logger("JobView"),

        tagName: "section",

        attributes: {
            id: "jobs"
        },

        events: {

        },

        collection: new Jobs.Collection(),

        displayedJobs: [],

        maxOnScreen: 20,

        filteredCount: 0,

        initialize: function () {

            var logger = this.logger,
                collection = this.collection,
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
                header = $('body > header'),
                footer = $('body > footer');

            collection.expected = this.$el.attr('data-count');

//          Use the url parameter provided as an option or get it in the data-url attribute of this element.

            if (this.options.url) {
                collection.url = this.options.url;
            } else if (this.$el.attr('data-url')) {
                collection.url = this.$el.attr('data-url');
            } else {
                W3.exception('No url parameter was specified');
            }

//          Listen on collection events

            collection.on('add', this.render, this);
            collection.on('reset', this.render, this);
            collection.on('destroy', this.render, this);

//          Parse the jobs already on the page if our collection is new

            if (collection.length == 0 && this.$('article').size() > 0) {
                try {
                    collection.reset(this.$('article').map(function (i, article) {
                        return Job.fromHtml(article);
                    }).toArray());
                } catch (ex) {
                    logger.error(ex);
                }
            }

//          Start the loader

            var loader = this.loader = new Loader(collection);
            loader.start({ sort: this.getSortParam(), search: this.getSearchParam() });

//          Open a socket and listen on jobupdate events

            this.socket = new W3.Socket(collection.url);
            this.socket.on("jobupdate", function (data) {
                var job = collection.get(data.id);
                if (!_.isUndefined(job)) {
                    job.set(data);
                } else {
                    logger.warn("unknown job with id: " + data.id);
                    logger.debug(data);
                }
            });

//          Add sorting handlers

            _.each(sortParams, function (param) {
                this.$("." + param + " .ascend").click(function (event) {
                    event.preventDefault();
                    sortLinks.removeClass("current");
                    $(this).addClass("current");
                    loader.setData({ sort: "-" + param, search: self.getSearchParam() });
                    collection.sortByParam(param);
                    return false;
                });
                this.$("." + param + " .descend").click(function (event) {
                    event.preventDefault();
                    sortLinks.removeClass("current");
                    $(this).addClass("current");
                    loader.setData({ sort: param, search: self.getSearchParam() });
                    collection.sortByParam(param, true);
                    return false;
                });
            }, this);

//          Add search handler

            $("#actions input[name=search]").bind("keyup change", function (event) {
                var input = this;
                setTimeout(function () {
                    var search = input.value;
                    self.filter = function (job) {
                        return job.get("name").indexOf(search) > -1 || job.get("entrypoint").indexOf(search) > -1;
                    };
                    loader.setData({ sort: self.getSortParam(), search: search });
                    self.render();
                }, 0);
            });

//          Add scroll handler

            var asideClone = aside.clone();
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



            $("#actions .search button").hide();
            $('nav.pagination :not(p.legend)').hide();
            footer.addClass('jsFixed');
            // TODO height + padding-top + padding-bottom
            //$('#main').css("padding-bottom", footer.height() + "px");
            $('#main').css("padding-bottom", "50px");

        },

        getSortParam: function () {
            var current = $(".sort .current");
            var param = current.parents("dt").attr("class");
            param = current.hasClass("ascend") ? "-" + param : param;
            return param;
        },

        getSearchParam: function () {
           return $("input[name=search]").val();
        },

        render: function () {

            this.logger.log("render");

//          Filter before rendering

            //this.collection.sort();

            var models = this.collection.models;

            if (_.isFunction(this.filter)) {
                models = _.filter(models, this.filter, this);
            }

            this.filteredCount = models.length;



            models = this.displayedJobs = models.slice(0, this.maxOnScreen);

            //this.displayedJobs = models;

//          Create job views and render

            var elements = models.map(function (job, index) {
                job.view = new Job.View({ model: job, template: this.options.jobTemplate });
                return job.view.render().el;
            }, this);

            this.$el.children('article').remove();

            this.$('.empty').remove();
            if (elements.length > 0) {
                this.$el.append(elements);
            } else {
                var empty = $('<p class="empty"></p>');
                if (this.getSearchParam() != "" && this.collection.expected != 0) {
                    empty.text("No search results");
                } else {
                    empty.html("No jobs have been configured yet. <a href='" + this.collection.url + "/new" + "'>Create your first job.</a>");
                }
                this.$el.append(empty);
            }

            this.updateLegend();
        },

        updateLegend: function () {

            var views = _.pluck(this.displayedJobs, 'view');
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
            this.maxOnScreen = visibles.last && visibles.last >= 20 ? visibles.last + 5 : 20;

            if (this.maxOnScreen != o) {
                this.render();
            }

            var total = this.getSearchParam() != "" ? this.filteredCount : this.collection.expected;

            var legend = $('.pagination .legend');

            if (visibles.first != null && visibles.last != null) {
                /*legend.text("Viewing " + visibles.first + "-" + visibles.last +
                    " of " + total + " results (" + this.collection.length + "/" + this.collection.expected + " - " + this.maxOnScreen + ")");*/
                legend.text("Viewing " + visibles.first + "-" + visibles.last + " of " + total + " results");
            } else {
                legend.text("No results");
            }



        }

    });

    return Jobs;

});