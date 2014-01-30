define([
    "util/Logger",
    "util/Util",
    "libs/backbone",
    "libs/jquery",
    "libs/underscore",
    "model/model"], function (Logger, Util, Backbone, $, _, Model) {

    "use strict";

    var logger = Logger.of("Job"),
        Job;

    Job = Model.extend({

        logger: Logger.of("Job"),

        defaults: {
            name: "New Job",
            entrypoint: "http://www.example.com",
            status: { status: "idle" }, // can be { status: "running", progress: 10 }
            completedOn: null, // .timestamp, .legend1, .legend2
            warnings: 0,
            errors: 0,
            resources: 0,
            maxResources: 0,
            health: -1
        },

        methodMap: {
            'run': 'POST',
            'stop': 'POST',
            'create': 'POST',
            'read': 'GET',
            'update': 'POST',
            'delete': 'POST'
        },

        search: function (search) {
            return this.get("name").toLowerCase().indexOf(search.toLowerCase()) > -1 ||
                this.get("entrypoint").toLowerCase().indexOf(search.toLowerCase()) > -1;
        },

        reportUrl: function () {
            return this.url() + "/resources";
        },

        isIdle: function () {
            return this.get("status").status === "idle";
        },

        isCompleted: function () {
            return this.get("completedOn") !== null;
        },

        run: function (options) {
            this.logger.info(this.get("name") + ": run");
            var action = this._serverEvent('run', options);
            this.logger.debug(action);

        },

        stop: function (options) {
            this._serverEvent('stop', options);
        },

        validate: function (attrs) {
            /*if (!attrs.name || attrs.name.length < 1) {
             logger.warn("Name required");
             return "Name required";
             }
             if (!attrs.entrypoint || attrs.entrypoint.length < 1) {
             logger.warn("Start URL required");
             return "Start URL required";
             }
             if (!attrs.maxResources || attrs.maxResources < 1 || attrs.maxResources > 5000) {
             logger.warn("Max resources must be between 1 and 5000");
             return "Max resources must be between 1 and 5000";
             }*/
        },

        _serverEvent: function (event, options) {
            var model, success, trigger, xhr;

            if (this.isNew()) {
                throw new Error("can't run actions on a new job");
            }

            options = options ? _.clone(options) : {};
            model = this;
            success = options.success;
            trigger = function () {
                model.trigger(event, model, model.collection, options);
            };

            options.success = function (resp) {
                if (options.wait) {
                    trigger();
                }
                if (success) {
                    success(model, resp);
                } else {
                    model.trigger('sync', model, resp, options);
                }
            };

            function wrapError(model, options) {
                var error = options.error;
                options.error = function (resp) {
                    if (error) { error(model, resp, options); }
                    model.trigger('error', model, resp, options);
                };
            }
            options.error = wrapError(model, options);
            options.dataType = "json";
            xhr = (this.sync || Backbone.sync).call(this, event, this, options);
            if (!options.wait) {
                trigger();
            }
            return xhr;
        },

        sync: function (method, model, options) {
            var type, params;
            type = this.methodMap[method];
            params = { type: type };
            if (!options.url) {
                params.url = model.url() || Util.exception("A 'url' property or function must be specified");
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
            return $.ajax(_.extend(params, options));
        },

        url: function () {
            return this.collection.url + "/" + this.get("id");
        }

    });

    Job.View = Job.View.extend({

        templateId: "job-template",

        events: {
            "click .stop": "stop",
            "click .run": "run",
            "click .delete": "_delete",
            "click .print": "print",
            "click .foldAllToggle": "foldAllToggle",
            "submit .searchForm": function () { return false; },
            //"keydown .dropdown" : "dropdown",
            "change [name='group']": "group"
            //"keyup [name=search]": "search"
        },

        unfoldAll: function () {
            if (this.model.collection.options.assertions) {
                this.model.collection.options.assertions.view.unfoldAll();
            }
        },

        foldAll: function () {
            if (this.model.collection.options.assertions) {
                this.model.collection.options.assertions.view.foldAll();
            }
        },

        foldAllToggle: function (event) {
            this.$el.toggleClass("foldAll");
            if (this.model.collection.options.assertions) {
                if (this.$el.hasClass("foldAll")) {
                    this.unfoldAll();
                    $(event.target).text("Collapse all");
                } else {
                    this.foldAll();
                    $(event.target).text("Expand all");
                }
            }
        },

        _delete: function () {
            var self = this;
            $("#choiceModal").map(function (i, modal) {
                var msg = $(".msg", modal),
                    yes = $(".yes", modal);
                msg.html("Are you sure you want to delete the job <strong>" + self.model.get("name") + "</strong>? This action cannot be reverted.");
                yes.unbind("click");
                yes.click(function (yes) { self.model.destroy({wait: true}); });
            });
        },

        print: function () {
            if (this.model.collection.options.assertions) {
                this.model.collection.options.assertions.view.render({dump: true});
            }
            if (this.model.collection.options.resources) {
                this.model.collection.options.resources.view.render({dump: true});
            }
            window.print();
            return false;
        },

        dropdown: function (event) {
            // Space = 32
            /*if (event.which === 32) {
             $("input:not(:checked)", event.target).click();
             }*/
        },

        group: function (event) {
            $(event.target).parents('form').submit();
        },

        init: function () {
            this.el.setAttribute("data-id", this.model.id);
        },

        templateOptions: function () {
            return {
                url: this.model.url(),
                isIdle: this.model.isIdle(),
                reportUrl: this.model.reportUrl(),
                isCompleted: this.model.get("completedOn") !== null,
                hasResources: function () {
                    return $("#resources").size() > 0;
                }
            };
        },

        getStatusString: function () {
            if (this.model.isIdle()) {
                return "Idle";
            } else {
                return "Running (" + this.model.get("status").progress + "%)";
            }
        },

        stop: function () {
            this.model.stop({ wait: true });
            return false;
        },

        run: function () {
            var self = this;
            $("#choiceModal").map(function (i, modal) {
                var msg = $(".msg", modal),
                    yes = $(".yes", modal);
                msg.html("Are you sure you want to re-run the job <strong>" + self.model.get("name") + "</strong>? This will consume up to <strong>" + self.model.get("maxResources") + "</strong> credits and previous results will be lost.");
                yes.unbind("click");
                yes.click(function (yes) {
                    self.$(".status .run").parents("form").attr("action", self.model.url()).submit();
                });
            });
        },

        addSearchHandler: function () {
            var collec = this.options.resources || this.options.assertions;
            if (!collec) {
                return;
            }
            this.$(".actions input[name=search]").bind("keyup change", function () {
                var input = this;
                setTimeout(function () {
                    collec.view.search(input.value, input);
                }, 0);
            });
        },

        afterRender: function () {
            this.addSearchHandler();
        },

        softRender: function (options) {
            //console.log("soft render");
            var html = $('<dl></dl>').html(this.template(options));
            this.$(".status").replaceWith(html.children(".status"));
            this.$(".completedOn").replaceWith(html.children(".completedOn"));
            this.$(".warnings").replaceWith(html.children(".warnings"));
            this.$(".errors").replaceWith(html.children(".errors"));
            this.$(".resources").replaceWith(html.children(".resources"));
            this.$(".health").replaceWith(html.children(".health"));
            this.$(".jobAction").replaceWith(html.find(".jobAction"));
            //this.delegateEvents();
        }

    });

    Job.fromHtml = function ($article) {
        var value = Util.valueFrom($article);
        return {
            id: $article.attr("data-id"),
            name: value('data-name'),
            entrypoint: value('data-entrypoint'),
            status: {
                status: value('data-status'),
                progress: value('data-progress')
            },
            completedOn: _.isUndefined(value('data-completed')) ? null : {
                timestamp: value('data-completed'),
                legend1: value('data-completed-legend1'),
                legend2: value('data-completed-legend2')
            },
            warnings: parseInt(value('data-warnings'), 10),
            errors: parseInt(value('data-errors'), 10),
            resources: parseInt(value('data-resources'), 10),
            maxResources: parseInt(value('data-max-resources'), 10),
            health: parseInt(value('data-health'), 10)
        };
    };

    return Job;

});