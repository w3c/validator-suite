/*(function($) {
    $.QueryString = (function(a) {
        if (a == "") return {};
        var b = {};
        for (var i = 0; i < a.length; ++i)
        {
            var p=a[i].split('=');
            if (p.length != 2) continue;
            b[p[0]] = decodeURIComponent(p[1].replace(/\+/g, " "));
        }
        return b;
    })(window.location.search.substr(1).split('&'))
})(jQuery);*/

(function () {

    "use strict";

    var W3, Job, JobView, Jobs, JobsView;

    W3 = window.W3 = (window.W3 || {});

    Job = W3.Job = Backbone.Model.extend({

        logger: new W3.Logger("Job"),

        defaults: {
            name: "New Job",
            //entrypoint: "",
            status: "idle",
            completedOn: {
                timestamp: null,
                legend1: "Never",
                legend2: null
            },
            warnings: 0,
            errors: 0,
            resources: 0,
            maxResources: 0,
            health: -1
        },

        methodMap: {
            run:    'POST',
            stop:   'POST',
            create: 'POST',
            read:   'GET',
            update: 'POST',
            delete: 'POST'
        },

        isIdle: function () {
            return this.get("status") === "idle";
        },

        isCompleted: function () {
            return this.get("completedOn").timestamp !== null;
        },

        run: function (options) {
            this.logger.info(this.get("name") + ": run");
            var action = this._serverEvent('run', options);
            this.logger.debug(action);
        },

        stop: function (options) { this._serverEvent('stop', options); },

        validate: function(attrs) {
            if (!attrs.name || attrs.name.length < 1) {
                this.logger.warn("Name required");
                return "Name required";
            }
            if (!attrs.entrypoint || attrs.entrypoint.length < 1) {
                this.logger.warn("Start URL required");
                return "Start URL required";
            }
            if (!attrs.maxResources || attrs.maxResources < 1 || attrs.maxResources > 500) {
                this.logger.warn("Max resources must be between 1 and 500");
                return "Max resources must be between 1 and 500";
            }
        },

        _serverEvent: function (event, options) {
            var model, success, trigger, xhr;

            if (this.isNew()) { throw new Error("can't run actions on a new job"); }

            options = options ? _.clone(options) : {};
            model = this;
            success = options.success;
            trigger = function () {
                model.trigger(event, model, model.collection, options);
            };

            options.success = function (resp) {
                if (options.wait) { trigger(); }
                if (success) {
                    success(model, resp);
                } else {
                    model.trigger('sync', model, resp, options);
                }
            };
            options.error = Backbone.wrapError(options.error, model, options);
            xhr = (this.sync || Backbone.sync).call(this, event, this, options);
            if (!options.wait) { trigger(); }
            return xhr;
        },

        sync: function (method, model, options) {
            var type, params;
            type = this.methodMap[method];
            params = { type: type };
            if (!options.url) {
                params.url = model.url() || W3.exception("A 'url' property or function must be specified");
            }
            if (method !== 'read') {
                params.data = { action: method };
            }
            if (!options.data && model && (method == 'create' || method == 'update')) {
                params.data = _.extend(
                    params.data,
                    {
                        id: model.id,
                        name: model.attributes.name, //; model.attributes
                        entrypoint: model.attributes.entrypoint,
                        maxResources: model.attributes.maxResources,
                        assertor: model.attributes.assertor
                    }
                );
            }

            this.logger.info("Sending request: ");
            this.logger.debug(_.extend(params, options));

            return $.ajax(_.extend(params, options));
        },

        log: function () { console.log(JSON.stringify(this.toJSON())); }
    });

    JobView = W3.JobView = Backbone.View.extend({

        // requires template option

        logger: new W3.Logger("JobView"),

        model: new Job(),

        tagName: "article",

        events: {
            "click .stop"   : "stop",
            "click .run"    : "run",
            "click .delete" : "_delete",
            "change [name=group]" : "group"
            //"keyup [name=search]": "search"
        },

        group: function (event) {
            $(event.target).parents('form').submit();
        },

        initialize: function () {
            this.el.setAttribute("data-id", this.model.id);
            this.model.on('change', this.render, this);
            this.model.on('destroy', this.remove, this);
        },

        /*search: function (event) {
            console.log(event);
            console.log(event.target.value);
            this.formOptions.search = event.target.value;
            this.formOptions.searchPosition = event.target.selectionStart;
            console.log(this.formOptions.searchPosition);
            this.render();
            return;
        },*/

        render: function () {
            this.$el.html(this.options.template(
                _.extend(
                    this.model.toJSON(),
                    {
                        url : this.model.url(),
                        isIdle: this.model.isIdle(),
                        isCompleted: this.model.get("completedOn").timestamp !== undefined
                        //search: this.formOptions.search,
                        //group: this.formOptions.group
                    }
                )
            ));
            return this;
        },
        stop: function () {
            this.model.stop({ wait: true });
            return false;
        },
        run: function () {
            this.model.run({ wait: true });
            return false;
        },
        _delete: function () {
            this.model.destroy({ wait: true });
            return false;
        },
        remove: function () {
            this.$el.remove();
        }

    });

    Jobs = W3.Jobs = Backbone.Collection.extend({
        url: '/suite/jobs',
        model: Job
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

    JobsView = W3.JobsView = Backbone.View.extend({

        logger: new W3.Logger("JobView"),

        collection: new Jobs(),

        initialize: function () {
            this.collection.on('add', this.addOne, this);
            this.collection.on('reset', this.addAll, this);
        },

        addOne: function (job) {
            var view = new JobView({ model: job, template: this.options.jobTemplate });
            this.$el.append(view.render().el);
        },

        addAll: function () {
            this.$el.children('article').remove();
            this.collection.each(this.addOne, this);
        }

    });

}());
