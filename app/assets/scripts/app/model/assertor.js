define(["model/Model"], function (Model) {

    "use strict";

    var Assertor;

    Assertor = Model.extend({

        defaults: {
            name: "None",
            errors: 0,
            warnings: 0
        },

        assertions: null,

        isValid: function () {
            return this.get("errors") + this.get("warnings") === 0;
        }

    });

    Assertor.View = Assertor.View.extend({

        templateId: "assertor-template",

        setCurrent: function () {
            this.$el.parents('section').children('article').removeClass('current');
            this.$el.addClass("current");
        },

        addFilterHandler: function () {},

        attributes: function () {
            var clas = this.model.isValid() ? "valid" : "";
            clas += this.isCurrent() ? " current" : "";
            return {
                "class": clas
            };
        },

        init: function () {
            this.el.setAttribute("data-id", this.model.id);
        },

        isCurrent: function () {
            if (!this.$el) { return false; }
            return this.$el.hasClass("current");
        }

    });

    Assertor.fromHtml = function (value, $elem) {
        return {
            id: $elem.attr('data-id'),
            name: value('data-name'),
            errors: parseInt(value('data-errors'), 10),
            warnings: parseInt(value('data-warnings'), 10)
        };
    };

    return Assertor;

});