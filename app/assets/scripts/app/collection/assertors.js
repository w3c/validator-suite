define(["lib/Logger", "model/assertor", "collection/Collection"], function (Logger, Assertor, Collection) {

    "use strict";

    var logger = new Logger("Assertors"),
        Assertors;

    Assertors = Collection.extend({

        model: Assertor

    });

    Assertors.View = Assertors.View.extend({

        templateId: "assertor-template",

        attributes: {
            id: "assertors"
        },

        getSortParam: function () {
            return {
                param: "errors",
                reverse: true
                //string: "errors"
            };
        },

        afterRender: function () {
            this.addFilterHandler();
            if (this.$('.current').size() === 0) {
                this.$("article:first-of-type .filter").click();
            }
        },

        addFilterHandler: function () {
            var filterLinks = this.$(".filter"),
                self = this;
            filterLinks.unbind('click');
            filterLinks.click(function (event) {
                event.preventDefault();
                filterLinks.parents('article').removeClass("current");
                $(this).parents('article').addClass("current");
                self.options.assertions.view().filterOn($(this).parents('article').attr('data-id'));
                return false;
            });
        }

    });

    return Assertors;

});