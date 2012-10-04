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

        collection: new Jobs.Collection(),

        tagName: "section",

        attributes: {
            id: "jobs"
        },

        events: {

        },

        initialize: function () {

            var logger = this.logger;
            var jobs = this.collection;

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

//          Get the total expected number of elements from the data-count attribute

            var count = this.$el.attr('data-count');

//          Fetch the initial elements

            var jobsView = this;
            var fetch = function (options) {
                var options = options ? options : {};
                if (!jobs.complete) {
                    options.silent = true;
                    options.success = _.bind(function (jobs_) {
                        if (jobs_.size() >= count) {
                            count = jobs_.size();
                            jobs.complete = true;
                        } else {
                            jobs.complete = false;
                        }
                        //_.bind(jobsView.render(), jobsView);
                        jobsView.render()
                    }, jobsView);
                    logger.info("collection is not complete: " + jobs.size() + "/" + count);
                    /*logger.info("fetching with options: ");
                    logger.debug(options);*/
                    return jobs.fetch(options);
                }
                logger.info("collection is complete: " + jobs.size() + "/" + count);
                return false;
            };

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

            var sortParams = [
                "name",
                "entrypoint",
                "status",
                "completedOn",
                "errors",
                "warnings",
                "resources",
                "maxResources",
                "health"
            ];
            var getSearchParam = this.getSearchParam;
            var sortLinks = this.$(".sort a");
            _.each(sortParams, function (param) {
                this.$("." + param + " .ascend").click(function (event) {
                    event.preventDefault();
                    sortLinks.removeClass("current");
                    $(this).addClass("current");
                    fetch({add: true, data: { sort: "-" + param, search: getSearchParam() }});
                    jobs.sortByParam(param);
                    return false;
                });
                this.$("." + param + " .descend").click(function (event) {
                    event.preventDefault();
                    sortLinks.removeClass("current");
                    $(this).addClass("current");
                    fetch({add: true, data: { sort: param, search: getSearchParam() }});
                    jobs.sortByParam(param, true);
                    return false;
                });
            }, this);

//          Add search handler

            var getSortParam = this.getSortParam;

            var searchEvent = function (event) {
                var search = this.value;
                jobsView.filter = function (job) {
                    return job.get("name").indexOf(search) > -1 || job.get("entrypoint").indexOf(search) > -1;
                };
                fetch({add: true, data: { search: search, sort: getSortParam() }});
                jobsView.render();
            };

            var input = $("#actions form.search input");
            input.keyup(searchEvent);
            input.change(searchEvent);

//          Add scroll handler

            var win = $(window);
            var aside = this.$('aside');
            var jobsSection = $('#jobs');
            //var asideClone = aside.clone();
            //aside.after(asideClone.hide());
            win.scroll(function (event) {
                if (jobsSection.offset().top > win.scrollTop()) {
                    //console.log("release");
                    aside.removeClass('jsFixed');
                    //aside.hide();
                    jobsSection.find('h2').css({
                        display: "none"
                    });
                } else {
                    //console.log("fixed");
                    //aside.css("top", win.scrollTop() - 60); // size of header
                    aside.addClass('jsFixed');
                    //aside.find('dt').offset({top: win.scrollTop(), left: 0});
                    //asideClone.show();
                    jobsSection.find('h2').css({
                        display: "block",
                        height: aside.height()
                    });
                    logger.debug(jobsSection.find('h2'));
                }
                event.preventDefault();
                //setTimeout($(document).trigger(event), 100);
                return false;
            });
            win.scroll();

            var footer = $('body > footer');
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
           return $("input.search").val();
        },

        add: function (job) {
            if (!job.collection) {
                this.collection.add(job, {silent: true});
            }
            /*if (!job.view) {
                job.view = new Job.View({ model: job, template: this.options.jobTemplate });
            }*/
            //this.$el.append(job.view.render().el);
            //this.render();
        },

        render: function () {
            this.logger.log("render");

            this.$el.children('article').remove();

//          Filter before rendering

            var jobs = this.collection;
            if (_.isFunction(this.filter)) {
                jobs = jobs.filter(this.filter, this);
            }

//          Create job views and render

            var elements = jobs.map(function (job) {
                if (!job.view) {
                    job.view = new Job.View({ model: job, template: this.options.jobTemplate });
                }
                return job.view.render().el;
            }, this);

            this.$el.append(elements);
        }

    });

    return Jobs;

});