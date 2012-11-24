define(["lib/Logger", "lib/Util", "lib/Socket", "libs/backbone"], function (Logger, Util, Socket, Backbone) {

    "use strict";

    var logger = new Logger("Model"),
        Model;

    Model = Backbone.Model.extend({

        initialize: function () {
            var view = this.view = new this.constructor.prototype.constructor.View(_.extend({ model: this }));
            view.render();

            if (_.isFunction(this.init)) { this.init(); }
        },

        listen: function () {
            var self = this;
            self.socket = new Socket(Util.getValue(this.url, this));
            self.socket.on("message", function (data) {
                if (data.id !== self.id) {
                    logger.error("Received an update with an incorrect id: " + data.id + ". Mine is: " + self.id);
                    return;
                }
                self.set(data);
            });
        },

        /*view: function (options) {
            if (this._view) { return this._view; }
            this._view = new this.constructor.prototype.constructor.View(_.extend({ model: this }, options));
            this._view.render();
            return this._view;
        },*/

        log: function () { logger.log(JSON.stringify(this.toJSON())); }

    });

    Model.View = Backbone.View.extend({

        tagName: "article",

        initialize: function () {
            this.model.on('change', this.render, this);
            this.model.on('destroy', this.remove, this);
            this.template = this.options.template || Util.getTemplate(this.templateId);
            if (_.isFunction(this.init)) { this.init(); }
            this.$cache = {
                footer: $('body > footer'),
                aside: $('#jobs aside'),
                win: $(window)
            };
        },

        render: function (options) {
            if (_.isFunction(this.beforeRender)) { this.beforeRender(); }
            options = _.extend(
                this.model.toJSON(),
                { view: this, model: this.model, Util: Util },
                options,
                _.isFunction(this.templateOptions) ? this.templateOptions() :
                        _.isObject(this.templateOptions) ? this.templateOptions : {}
            );
            this.$el.html(this.template(options));
            if (_.isFunction(this.afterRender)) { this.afterRender(); }
            return this;
        },

        isVisible: function () {

            var footer = this.$cache.footer,
                aside = this.$cache.aside,
                win = this.$cache.win,
                top = this.$el.offset().top,
                bottom = this.$el.offset().top + this.$el.height();

            return (top > win.scrollTop() + aside.height() &&
                //      top < win.scrollTop() + win.height() - footer.height());
                bottom <= win.scrollTop() + win.height() - footer.height());

        },

        isScrolledUp: function () {
            var footer = this.$cache.footer,
                win = this.$cache.win,
                top = this.$el.offset().top;
            return (top <= win.scrollTop() + win.height() - footer.height());
        },

        _delete: function () {
            this.model.destroy({ wait: true });
            return false;
        },

        remove: function () {
            this.$el.remove();
        }

    });

    return Model;

});