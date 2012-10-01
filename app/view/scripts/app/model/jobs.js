define(["model/job", "vs", "libs/backbone"], function (Job, W3, Backbone) {

    "use strict";

    var Jobs = {};

    Jobs.Collection = Backbone.Collection.extend({
        url: '/suite/jobs',
        model: Job.Model
        /*comparator: function (job1, job2) {
         if (job1.get("errors") > job2.get("errors")) {
         return -1;
         } else if (job1.get("errors") === job2.get("errors")) {
         if (job1.get("url") < job2.get("url"))
         return -1;
         else
         return +1;
         } else {
         return +1;
         }
         }*/
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
            this.collection.on('add', this.add, this);
            this.collection.on('reset', this.render, this);
        },

        add: function (job) {
            var view = new Job.View({ model: job, template: this.options.jobTemplate });
            this.$el.append(view.render().el);
        },

        render: function () {
            this.$el.children('article').remove();
            var elements = this.collection.map(function (job) {
                //this.addOne
                this.logger.debug(job);
                var view = new Job.View({ model: job, template: this.options.jobTemplate });
                return view.render().el;
            }, this);
            this.$el.append(elements);
        }

    });

    return Jobs;

});