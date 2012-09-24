(function (){

    var W3 = window.W3 = (window.W3 || {});

    var Job = W3.Job = Backbone.Model.extend({

        defaults: {
            id: 0,
            name: "",
            entrypoint: "",
            status: "idle",
            completedOn: {
                timestamp: "",
                legend1: "",
                legend2: ""
            },
            warnings: 0,
            errors: 0,
            resources: 0,
            maxResources: 0,
            health: {
                value: -1,
                legend: ""
            }
        },

        methodMap: {
            'on':'POST',
            'off':'POST',
            'stop':'POST',
            'run':'POST',
            'create':'POST',
            'read':'GET',
            'update':'POST',
            'delete':'POST'
        },

        isIdle: function () {
            return this.get("status") == "idle";
        },

        //putOn: function(options) {this._serverEvent('on', options);},

        //putOff: function(options) {this._serverEvent('off', options);},

        stop: function (options) {this._serverEvent('stop', options);},

        run: function (options) {this._serverEvent('run', options);},

        _serverEvent: function (event, options) {
            options = options ? _.clone(options) : {};
            var model = this;
            var success = options.success;

            var trigger = function () {
                model.trigger(event, model, model.collection, options);
            };

            if (this.isNew()) return trigger();
            options.success = function(resp) {
                if (options.wait) trigger();
                if (success) {
                    success(model, resp);
                } else {
                    model.trigger('sync', model, resp, options);
                }
            };
            options.error = Backbone.wrapError(options.error, model, options);
            var xhr = (this.sync || Backbone.sync).call(this, event, this, options);
            if (!options.wait) trigger();
            return xhr;
        },

        sync: function (method, model, options) {
            var type = this.methodMap[method];
            var params = {type: type};
            if (!options.url)
                params.url = model.url() || exception("A 'url' property or function must be specified");
            if (method != 'read')
                params.data = {action: method};
            // Don't process data on a non-GET request.
            //if (params.type !== 'GET')
            //params.processData = false;
            return $.ajax(_.extend(params, options));
        },

        /*syncData: function(jobData) {
         //if (jobData.get("lastCompleted") == "Never")
         //    jobData.set("lastCompleted", this.get("data").get("lastCompleted"));
         this.set({data: jobData});
         },*/

        log: function () {console.log(JSON.stringify(this.toJSON()));}
    });

    var Jobs = W3.Jobs = Backbone.Collection.extend({
        url: '/suite/jobs',
        model: this.Job
    });

    var JobView = W3.JobView = Backbone.View.extend({

        // requires template option

        tagName : "article",

        attributes: {
            "data-id": "0",
            "class": "job"
        },

        events: {
            //"click .edit"   : "edit",
            //"click .on"     : "putOn",
            //"click .off"    : "putOff",
            "click .stop"   : "stop",
            "click .run"    : "run",
            "click .delete" : "_delete"
        },

        initialize: function () {
            if(this.model !== undefined) {
                this.attributes["data-id"] = this.model.id;
                this.model.on('change', this.render, this);
                this.model.on('destroy', this.remove, this);
                //this.model.on('run', function () {alert("run");}, this);
                //this.model.on('stop', function () {alert("run");}, this);
            }
        },

        render: function () {

            this.$el.html(this.options.template(
                _.extend(
                    this.model.toJSON(),
                    {
                        url : this.model.url(),
                        isIdle: this.model.isIdle(),
                        isCompleted: this.model.get("completedOn").timestamp != undefined
                    }
                )
            ));
            return this;
        },
        /*edit: function () {
            window.location = this.model.url() + "/edit";
            return false;
        },
        putOn: function () {
            this.model.putOn({wait:true});
            return false;
        },
        putOff: function () {
            this.model.putOff({wait:true});
            return false;
        },*/
        stop: function () {
            this.model.stop({wait:true});
            //console.log("stop");
            return false;
        },
        run: function () {
            this.model.run({wait:true});
            //try { window.Report.articles.reset(); } catch(ex) { }
            return false;
        },
        _delete: function () {
            this.model.destroy({wait:true});
            return false;
        },
        remove: function () {
            this.$el.remove();
        }

    });

    var JobsView = W3.JobsView = Backbone.View.extend({

        initialize: function () {
            this.jobs = new Jobs();
            this.jobs.on('add', this.addOne, this);
            this.jobs.on('reset', this.addAll, this);
        },

        addOne: function(job) {
            var view = new JobView({model: job, template: this.options.jobTemplate});
            this.$el.append(view.render().el);
        },

        addAll: function() {
            this.$el.children('.job').remove();
            this.jobs.each(this.addOne, this);
        }

    });

})();
