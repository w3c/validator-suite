define(["util/Logger", "util/Util", "model/assertor", "model/collection"], function (Logger, Util, Assertor, Collection) {

    "use strict";

    var Assertors = Collection.extend({

        logger:Logger.of("Assertors"),

        model:Assertor,

        comparator:function (o1, o2) {
            if (o1.get("errors") > o2.get("errors")) {
                return -1;
            } else if (o1.get("errors") === o2.get("errors")) {
                if (o1.get("warnings") > o2.get("warnings")) {
                    return -1;
                } else if (o1.get("warnings") === o2.get("warnings")) {
                    return 0;
                } else {
                    return +1;
                }
            } else {
                return +1;
            }
        }

    });

    Assertors.View = Assertors.View.extend({

        attributes:{
            id:"assertors"
        },

        getSortParam:function () {
            return {
                param:"errors",
                reverse:true
                //string: "errors"
            };
        },

        beforeRender:function () {
            this.collection.sort({silent:true}); // not sure why this is necessary
        },

        afterRender:function () {
            this.addFilterHandler();
            if (this.$('.current').size() === 0) {
                this.$("article:first-of-type .filter").click();
            }
            //console.log("Assertors rendered");
        },

        addFilterHandler:function () {
            var filterLinks = this.$(".filter"),
                self = this;
            filterLinks.unbind('click');
            filterLinks.click(function (event) {
                event.preventDefault();
                filterLinks.parents('article').removeClass("current");
                $(this).parents('article').addClass("current");
                self.options.assertions.view.filterOn($(this).parents('article').attr('data-id'));
                return false;
            });
        },

        init:function () {
            var assertions = this.options.assertions,
                assertors = this.collection,
                self = this;
            assertions.on("change reset", function () {
                var assertorCounts = {};
                assertions.map(function (assertion) {
                    var assertor = assertion.get("assertor"),
                        level = assertion.get("severity"),
                        occurrences = assertion.get("occurrences");

                    if (!_.isUndefined(assertorCounts[assertor])) {
                        assertorCounts[assertor][level] = assertorCounts[assertor][level] + (occurrences !== 0 ? occurrences : 1);
                    } else {
                        assertorCounts[assertor] = {};
                        assertorCounts[assertor].error = 0;
                        assertorCounts[assertor].warning = 0;
                        assertorCounts[assertor].info = 0;
                        assertorCounts[assertor][level] = (occurrences !== 0 ? occurrences : 1);
                    }
                });
                var assertor;
                for (assertor in assertorCounts) {
                    //console.log(assertor);
                    //console.log(assertorCounts[assertor]);
                    if (!_.isUndefined(assertors.get(assertor))) {
                        assertors.get(assertor).set({
                            errors:assertorCounts[assertor].error,
                            warnings:assertorCounts[assertor].warning
                        });
                    } else {
                        assertors.add({
                            id:assertor,
                            name:Util.getAssertorName(assertor),
                            errors:assertorCounts[assertor].error,
                            warnings:assertorCounts[assertor].warning
                        });
                    }
                }
                self.render();


            });
        }

    });

    return Assertors;

});