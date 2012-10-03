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
        }
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

            if (this.options.url)
                jobs.url = this.options.url;
            jobs.on('add', this.add, this);
            jobs.on('reset', this.render, this);

            // Sorting
            var sortParams = ["name", "entrypoint", "status", "completedOn", "errors", "warnings", "resources", "maxResources", "health"];
            var sortLinks = this.$(".sort a");
            _.each(sortParams, function (param) {
                this.$("." + param + " .ascend").click(function (event) {
                    event.preventDefault();
                    sortLinks.removeClass("current");
                    $(this).addClass("current");
                    jobs.sortByParam(param);
                    return false;
                });
                this.$("." + param + " .descend").click(function (event) {
                    event.preventDefault();
                    sortLinks.removeClass("current");
                    $(this).addClass("current");
                    jobs.sortByParam(param, true);
                    return false;
                });
            }, this);

            // Search
            var jobsView = this;
            $("#actions form.search input").keyup(function (event) {
                var search = this.value;
                jobsView.filter = function (job) {
                    if (job.get("name").indexOf(search) > -1 || job.get("entrypoint").indexOf(search) > -1) {
                        return true;
                    } else {
                        return false;
                    }
                };
                jobsView.render();
            });
            $("#actions form.search button").remove();

        },

        add: function (job) {
            if (!job.collection) {
                this.collection.add(job, {silent: true});
            }
            if (!job.view) {
                job.view = new Job.View({ model: job, template: this.options.jobTemplate });
            }
            this.$el.append(job.view.render().el);
        },

        render: function () {
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