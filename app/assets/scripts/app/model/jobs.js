define(["w3", "libs/backbone", "model/job"], function (W3, Backbone, Job) {

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
        complete: false
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

        expectedCount: 0,

        screenCount: 0,

        currentCount: function () { return this.collection.length; },

        initialize: function () {

            var logger = this.logger,
                jobs = this.collection,
                jobsSection = this.$el,
                jobsView = this,
                sortLinks = this.$(".sort a"),
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
                getSortParam = this.getSortParam,
                getSearchParam = this.getSearchParam,
                updateLegend = this.updateLegend,
                expectedCount = this.expectedCount = this.$el.attr('data-count'),
                win = $(window),
                aside = this.$('aside'),
                header = $('body > header'),
                footer = $('body > footer');


//          Use the url parameter provided as an option or get it in the data-url attribute of this element.

            if (this.options.url) {
                jobs.url = this.options.url;
            } else if (this.$el.attr('data-url')) {
                jobs.url = this.$el.attr('data-url');
            } else {
                W3.exception('No url parameter was specified');
            }

//          Listen on collection events

            jobs.on('add', this.add, this);
            jobs.on('reset', this.render, this);

            //jobs.on('add', function (job) {console.log(job)}, this);

//          Get the total expected number of elements from the data-count attribute


//          Fetch the initial elements

            var xhr, xhrCounter = 0, lastOptions;
            var fetch = function (options) {
                logger.debug(options);
                var options = options ? options : {};
                if (!jobs.complete) {
                    lastOptions = options;
                    // 0: aborted, 4: finished. others mean pending
                    if (xhr && xhr.readyState != 0 && xhr.readyState != 4) {
                        if (lastOptions && _.isEqual(lastOptions.data, options.data)) {
                            logger.info('same as pending request, ignoring');
                            return;
                        }
                        if (options && options.data.offset) {
                            logger.info("can't fetch more elements during a pending sort/search, ignoring");
                            return;
                        }
                        logger.debug(xhr);
                        logger.info('aborting previous request');
                        xhr.abort();
                    }
                    var token = ++xhrCounter;
                    options.silent = true;
                    options.success = _.bind(function (jobs_) {
                        //setTimeout(function () {

                            if (token != xhrCounter) {
                                logger.warn("old xhr request");
                                return;
                            }

                            if (jobs_.size() >= expectedCount) {
                                expectedCount = jobs_.size();
                                jobs.complete = true;
                                logger.info("Collection completed: " + jobs.size() + "/" + expectedCount);
                            } else {
                                jobs.complete = false;
                                logger.info("Collection completion: " + jobs.size() + "/" + expectedCount);
                            }
                            jobsView.render();
                        //}, 0);
                        //_.bind(jobsView.render(), jobsView);
                    }, jobsView);

                    //_.extends(options.data, {timestamp: (new Date())}

                    xhr = jobs.fetch(options);
                    return xhr;
                } else {
                    return false;
                }
            };

            // TODO only if .list
            fetch();

//          Open a socket and listen on jobupdate events

            this.socket = new W3.Socket(jobs.url);
            this.socket.on("jobupdate", function (data) {
                var job = jobs.get(data.id);
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
                    fetch({data: { sort: "-" + param, search: getSearchParam() }});
                    jobs.sortByParam(param);
                    return false;
                });
                this.$("." + param + " .descend").click(function (event) {
                    event.preventDefault();
                    sortLinks.removeClass("current");
                    $(this).addClass("current");
                    fetch({data: { sort: param, search: getSearchParam() }});
                    jobs.sortByParam(param, true);
                    return false;
                });
            }, this);

//          Add search handler

            $("#actions input[name=search]").bind("keyup change", function (event) {
                var input = this;
                setTimeout(function () {
                    var search = input.value;
                    jobsView.filter = function (job) {
                        return job.get("name").indexOf(search) > -1 || job.get("entrypoint").indexOf(search) > -1;
                    };
                    fetch({data: { search: search, sort: getSortParam() }});
                    jobsView.render();
                }, 0);
                return;
            });

//          Add scroll handler

            function isVisible (elm) {
                var header = $('body > header'),
                    footer = $('body > footer');

                if (elm.offset() == null)
                    return false;

                var top = elm.offset().top;
                //var bottom = elm.offset().top + elm.height();

                return (top > $(window).scrollTop() + header.height() &&
                        top < $(window).scrollTop() + $(window).height() - footer.height());
                        //bottom < $(window).scrollTop() + $(window).height() - footer.height());

            }

            var asideClone = aside.clone();

            win.bind("scroll resize", function (event) {
                setTimeout(function () {
//              Set the headers to a fixed position if above the viewport.

                if (jobsSection.offset().top > win.scrollTop()) {
                    aside.removeClass('jsFixed');
                    asideClone.remove();
                    /*jobsSection.find('h2').css({
                        display: "none"
                    });*/
                } else {
                    aside.before(asideClone);
                    aside.addClass('jsFixed');
                    /*jobsSection.find('h2').css({
                        display: "block",
                        height: aside.height()
                    });*/
                }

//              Fetch new elements if end of page

                //var lastArticle = $('#jobs article:nth-last-of-type(2)');
                var lastArticle = $('#jobs article:last-of-type');
                if (isVisible(lastArticle)) {
                    fetch({
                        add: true,
                        data: {
                            search: getSearchParam(),
                            sort: getSortParam(),
                            offset: $('#jobs article').size() // jobs.size()
                        }
                    });
                }

                jobsView.updateLegend();
                }, 0);

            });
            win.scroll();

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

        add: function (job) {
            if (!job.collection) {
                this.collection.add(job, { silent: true });
            }
            /*if (!job.view) {
                job.view = new Job.View({ model: job, template: this.options.jobTemplate });
            }*/
            //this.$el.append(job.view.render().el);
            this.render();
        },

        render: function () {
            this.logger.log("render");

            this.$el.children('article').remove();

//          Filter before rendering

            var jobs = this.collection;
            if (_.isFunction(this.filter)) {
                jobs = jobs.filter(this.filter, this);
            }

//          Set the current count on screen

            this.countOnScreen = jobs.length;

//          Create job views and render

            var elements = jobs.map(function (job) {
                job.view = new Job.View({ model: job, template: this.options.jobTemplate });
                return job.view.render().el;
            }, this);

            this.$el.append(elements);

            //var a = this.$el.append;

            //_.each(elements, a);

            this.updateLegend();
        },

        updateLegend: function () {

            var aside = this.$('aside');

            function isVisible (elm) {
                var footer = $('body > footer');

                if (elm.offset() == null)
                    return false;

                var top = elm.offset().top;
                //var bottom = elm.offset().top + elm.height();

                return (top > $(window).scrollTop() + aside.height() &&
                    top < $(window).scrollTop() + $(window).height() - footer.height());
                    // bottom < $(window).scrollTop() + $(window).height() - footer.height());
            }

            var visibles = _.reduce(this.$('article').toArray(), function (memo, article, index) {
                    var index = index + 1;
                    var visible = isVisible($(article));
                    if (memo.first == null) {
                        memo.first = visible ? index : null;
                    }
                    memo.last = visible ? index : memo.last;
                    return memo;
                },
                {
                    first: null,
                    last: null
                }
            );

            var legend = $('.pagination .legend');

            legend.text("Viewing " + visibles.first + "-" + visibles.last + " of " + this.expectedCount + " results");

        }

    });


    return Jobs;

});