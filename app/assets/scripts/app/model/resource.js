define(["w3", "libs/backbone"], function (W3, Backbone) {

    "use strict";

    var Resource = {};

    Resource.Model = Backbone.Model.extend({

        logger: new W3.Logger("Resource"),

        defaults: {
            resourceUrl: "",
            /*lastValidated: {
                timestamp: null,
                legend1: "Never",
                legend2: null
            },*/
            lastValidated: null,
            warnings: 0,
            errors: 0
        },

        initialize: function () {
            this.id = this.get("resourceUrl");
        },

        methodMap: {
            /*'create': 'POST',
            'read':   'GET',
            'update': 'POST',
            'delete': 'POST'*/
        },

        validate: function (attrs) {
            /*if (!attrs.resourceUrl || attrs.resourceUrl.length < 1) {
                this.logger.warn("Resource url required");
                return "Resource url required";
            } */
        },

        log: function () { console.log(JSON.stringify(this.toJSON())); }
    });

    Resource.View = Backbone.View.extend({

        // requires template option

        logger: new W3.Logger("JobView"),

        model: new Resource.Model(),

        tagName: "article",

        events: {
            //"click .stop"   : "stop",
            //"click .run"    : "run",
            //"click .delete" : "_delete",
            //"change [name=group]" : "group"
            //"keyup [name=search]": "search"
        },

        initialize: function () {
            //this.el.setAttribute("data-id", this.model.id);
            this.model.on('change', this.render, this);
            this.model.on('destroy', this.remove, this);
        },

        render: function () {
            this.$el.html(this.options.template(
                _.extend(
                    this.model.toJSON(),
                    {
                        url : this.model.url(),
                        //isIdle: this.model.isIdle(),
                        //isCompleted: this.model.get("completedOn") !== null,
                        Util: W3.Util
                        //search: this.formOptions.search,
                        //group: this.formOptions.group
                    }
                )
            ));
            return this;
        },

        isVisible: (function () {

            // cache those vars
            var footer = $('body > footer');
            var win = $(window);
            var aside = $('#jobs aside');

            return function () {
                var top = this.$el.offset().top;
                var bottom = this.$el.offset().top + this.$el.height();

                return (top > win.scrollTop() + aside.height() &&
                //      top < win.scrollTop() + win.height() - footer.height());
                        bottom <= win.scrollTop() + win.height() - footer.height());

            }
        })(),

        isScrolledUp: (function () {

            // cache those vars
            var footer = $('body > footer');
            var win = $(window);

            return function () {
                var top = this.$el.offset().top;
                return (top <= win.scrollTop() + win.height() - footer.height());
            }
        })(),

        _delete: function () {
            this.model.destroy({ wait: true });
            return false;
        },

        remove: function () {
            this.$el.remove();
        }

    });

    Resource.fromHtml = function (elem) {
        var $elem = $(elem);
        var _value = function (attribute) {
            var tag = $elem.find('[' + attribute + ']');
            var attr = tag.attr(attribute);
            if (attr !== "") {
                return attr;
            } else {
                return tag.text();
            }
        };

        var resourceObj = {
            resourceUrl: _value('data-resourceUrl'),
            //lastValidated: _value('data-lastValidated'),
            lastValidated: {
                timestamp: _value('data-lastValidated'),
                legend1: _value('data-lastValidated-legend1'),
                legend2: _value('data-lastValidated-legend2')},
            warnings: parseInt(_value('data-warnings')),
            errors: parseInt(_value('data-errors'))
        };

        console.log(resourceObj);

        return new Resource.Model(resourceObj);
    };

    return Resource;

});