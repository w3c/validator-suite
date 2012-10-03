define(["w3", "libs/backbone", "model/job"], function (W3, Backbone, Job) {

    "use strict";

    var Jobs = {};

    var getComparatorBy = function (param1, param2) {
        var comparator = function (o1, o2) {
            if (o1.get(param1) > o2.get(param1)) {
                console.log(o1.get(param1) + " | " + o2.get(param1));
                return +1;
            } else if (o1.get(param1) === o2.get(param1) && o1.get(param2) > o2.get(param2)) {
                console.log(o1.get(param1) + " | " + o2.get(param1) + " | " + o1.get(param2) + " | " + o2.get(param2));
                return +1;
            } else if (o1.get(param1) === o2.get(param1) && o1.get(param2) === o2.get(param2)) {
                console.log(o1.get(param1) + " | " + o2.get(param1) + " | " + o1.get(param2) + " | " + o2.get(param2));
                return 0;
            } else {
                return -1;
            }
        }
        return comparator;
    };

    var j4 = new Job.Model({name: "job2", entrypoint: "http://www.w3.org", warnings: 1, errors: 1});
    var j3 = new Job.Model({name: "job1", entrypoint: "http://www.w3.org", warnings: 1, errors: 2});
    var j2 = new Job.Model({name: "job3", entrypoint: "http://www.w3.org", warnings: 2, errors: 2});
    var j1 = new Job.Model({name: "job4", entrypoint: "http://www.w3.org", warnings: 3, errors: 2});

    var c = getComparatorBy("errors", "name");
    c(j1, j2);
    /*console.log(c(j1, j3));
    console.log(c(j2, j3));
    console.log(c(j2, j4));*/

    Jobs.Collection = Backbone.Collection.extend({
        model: Job.Model,
        comparator: getComparatorBy("errors", "warnings")
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
            if (this.options.url)
                this.collection.url = this.options.url;
            this.collection.on('add', this.add, this);
            this.collection.on('reset', this.render, this);
            var jobs = this.collection;
            this.$('.name .ascend').click(function () {
                jobs.sortBy("name");
            });
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
            console.log(this.collection);
            var elements = this.collection.map(function (job) {
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