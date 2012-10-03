define(["w3", "libs/backbone"], function (W3, Backbone) {

    "use strict";

    var Job = {};

    Job.Model = Backbone.Model.extend({

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
            'run':    'POST',
            'stop':   'POST',
            'create': 'POST',
            'read':   'GET',
            'update': 'POST',
            'delete': 'POST'
        },

        isIdle: function () {
            return this.get("status") === "idle";
        },

        isCompleted: function () {
            return this.get("completedOn") !== null;
        },

        run: function (options) {
            this.logger.info(this.get("name") + ": run");
            var action = this._serverEvent('run', options);
            this.logger.debug(action);
        },

        stop: function (options) { this._serverEvent('stop', options); },

        validate: function (attrs) {
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
            if (!options.data && model && (method === 'create' || method === 'update')) {
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

    Job.View = Backbone.View.extend({

        // requires template option

        logger: new W3.Logger("JobView"),

        model: new Job.Model(),

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
                        isCompleted: this.model.get("completedOn") !== null,
                        W3: W3
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

    return Job;

});