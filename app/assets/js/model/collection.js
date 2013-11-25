define(["util/Logger", "libs/backbone", "util/Util", "util/Socket", "libs/underscore", "libs/jquery"], function (Logger, Backbone, Util, Socket, _, $) {

    "use strict";

    var Collection;

    function getComparatorBy(param, reverse) {

        reverse = (reverse || false);

        if (param === "lastValidated" || param === "completedOn") {
            return function (o1, o2) {
                var p1 = o1.get(param),
                    p2 = o2.get(param);
                if (p1 === null && p2 === null) {
                    return 0;
                }
                if (p2 === null) {
                    return reverse ? -1 : +1;
                }
                if (p1 === null) {
                    return reverse ? +1 : -1;
                }
                if (p1.timestamp > p2.timestamp) {
                    return reverse ? -1 : +1;
                }
                if (p1.timestamp === p2.timestamp) {
                    return 0;
                }
                return reverse ? +1 : -1;
            };
        }

        return function (o1, o2) {
            if (o1.get(param) > o2.get(param) || (_.isEqual(o1.get(param), o2.get(param)) && o1.id > o2.id)) {
                return reverse ? -1 : +1;
            }
            if (_.isEqual(o1.get(param), o2.get(param)) && o1.id === o2.id) {
                return 0;
            }
            return reverse ? +1 : -1;
        };
    }

    Collection = Backbone.Collection.extend({

        //comparator: getComparatorBy("id"),

        logger: Logger.of("UnNamed-Collection"),

        sortByParam: function (param, reverse, options) {
            this.comparator = getComparatorBy(param, reverse);
            this.sort(options);
        },

        isComplete: function () {
            return this.expected && this.length < this.expected ? false :  true;
        },

        initialize: function () {
            var self = this;
            this.on('add', function () {
                if (!_.isUndefined(self.expected) && self.expected < self.length) {
                    self.expected = self.length;
                }
            });
            if (_.isFunction(this.init)) {
                this.init();
            }
        },

        configure: function (options) {
            options = this.options = (options || {});
            this.view = new this.constructor.prototype.constructor.View(_.extend({ collection: this }, options));
            if (options.listen || (_.isUndefined(options.listen))) {
                if (this.view.isList()) {
                    this.listen();
                } else if (this.view.isSingle()) {
                    if (this.at(0)) {
                        this.at(0).listen();
                    }
                }
            }
            return this;
        },

        listen: function () {

            var self = this;

            this.socket = new Socket(this.url);
            self.socket.on("message", function (datas) {
                var changedModels = [];
                _.map(datas, function (data) {
                    var model = self.get(data.id);
                    if (!_.isUndefined(model)) {
                        changedModels.push(model);
                        model.set(data, {silent: true});
                    } else {
                        self.add(new self.model(data, {collection: self}), {silent: true});
                    }
                });

                self.trigger("change");
                // XXX: Fix this. Below is necessary because collection.render does not recomputes each model view
                _.each(changedModels, function (model) {
                    model.trigger("change");
                });
            });
        }

    });

    Collection.View = Backbone.View.extend({

        displayed: [],

        maxOnScreen: 30,

        filteredCount: 0,

        sortParams: [],

        search: function (search, searchInput) {
            this.currentSearch = search;
            this.search_ = (_.isString(search) && search !== "") ? function (model) {
                return model.search(search);
            } : undefined;
            this.render();
        },

        initialize: function () {

            this.logger = this.collection.logger;

            var collection = this.collection,
                initial_sort = this.getSortParam();

            if (this.options.url) {
                collection.url = this.options.url;
            } else if (this.$el.attr('data-url')) {
                collection.url = this.$el.attr('data-url');
            } else {
                Util.exception('No url parameter was specified');
            }

            if (this.options.expected) {
                collection.expected = this.options.expected;
            } else if (this.$el.attr('data-count')) {
                collection.expected = parseInt(this.$el.attr('data-count'), 10);
            } else {
                Util.exception('No count parameter was specified');
            }

            collection.on('add reset change', _.throttle(this.render, 250), this);
            collection.on('destroy', this.render, this);
            collection.on('sort', this.render, this);

            this.addSortHandler();
            //this.addSearchHandler();

            if (this.isList()) {
                this.addScrollHandler();
            }

            if (_.isUndefined(this.options.loadFromMarkup) || this.options.loadFromMarkup) {
                this.logger.log("loadFromMarkup");
                this.loadFromMarkup();
            }

            if (!_.isFunction(collection.comparator)) {
                collection.sortByParam(initial_sort.param, initial_sort.reverse, { silent: true });
            }
            /*else {
             //collection.sort({ silent: true });
             }*/

            if (_.isFunction(this.init)) {
                this.init();
            }

        },

        loadFromMarkup: function () {
            var collection = this.collection,
                models;

            if (!_.isFunction(collection.model.fromHtml)) {
                this.logger.error("fromHtml function not provided");
                return false;
            }

            models = this.$('article').map(function (i, article) {
                var $article = $(article),
                    value = Util.valueFrom($article);
                return new collection.model.fromHtml($(article));
            }).toArray();

            this.logger.info("parsed " + models.length + " model(s) from the page.");

            collection.reset(models);
        },

        getSortParam: function () {
            var current = this.$(".sort .current"),
                param = current.parents("dt").attr("class"),
                reverse = !current.hasClass("ascend");
            return {
                param: param,
                reverse: reverse,
                string: reverse ? param : "-" + param
            };
        },

        render: function (options) {

            options = (options || {});

            //console.log("render");

            if (_.isFunction(this.beforeRender)) {
                this.beforeRender();
            }

            var models = this.collection.models,
                elements,
                empty,
                self = this,
                emptyMessage;

            if (_.isFunction(this.search_)) {
                models = _.filter(models, this.search_);
            }

            if (_.isFunction(this.filter)) {
                models = _.filter(models, this.filter);
            }

            this.filteredCount = models.length;

            if (options.dump) {
                this.displayed = models;
            } else {
                models = this.displayed = models.slice(0, this.maxOnScreen);
            }

            elements = _.map(models, function (model) {
                return model.view.el;
            });

            this.$('.empty').remove();

            this.$el.children('article').detach();

            if (elements.length > 0) {
                _.map(elements, function (elem) {
                    this.$el.append(elem);
                    this.$el.append(" ");
                }, this);
            } else {
                //empty = $('<p class="empty"></p>');
                //this.$el.append(empty);
                this.$el.append('<p class="empty"></p>');

                emptyMessage = (function () {
                    if (_.isFunction(self.emptyMessage)) {
                        return self.emptyMessage();
                    } else if (_.isString(self.emptyMessage)) {
                        return self.emptyMessage;
                    } else {
                        self.logger.warn("No emptyMessage function or value provided");
                        return "";
                    }
                }());

                if (this.collection.expected === 0) {
                    this.$('.empty').html(emptyMessage);
                } else if (this.currentSearch && this.currentSearch !== "") {
                    this.$('.empty').html("No search result.");
                }
                /*else {
                 // TODO: loading no?
                 empty.html(emptyMessage);
                 }*/
            }

            if (this.isList() && (_.isUndefined(this.options.updateLegend) || this.options.updateLegend)) {
                this.updateLegend();
            }

            if (_.isFunction(this.afterRender)) {
                this.afterRender();
            }

        },

        addSortHandler: function () {
            var sortLinks = this.$(".sort a"),
                self = this;
            _.each(this.sortParams, function (param) {
                var ascend = this.$("." + param + " .ascend"),
                    descend = this.$("." + param + " .descend");
                ascend.unbind('click');
                descend.unbind('click');
                ascend.click(function (event) {
                    event.preventDefault();
                    sortLinks.removeClass("current");
                    $(this).addClass("current");
                    self.collection.sortByParam(param);
                    return false;
                });
                descend.click(function (event) {
                    event.preventDefault();
                    sortLinks.removeClass("current");
                    $(this).addClass("current");
                    self.collection.sortByParam(param, true);
                    return false;
                });
            }, this);

        },

        /*addSearchHandler: function () {
         if (!this.options.searchInput) { return; }
         var self = this;
         this.options.searchInput.unbind("keyup change");
         this.options.searchInput.bind("keyup change", function () {
         var input = this;
         setTimeout(function () {
         if (self.loader) { self.loader.setData({ sort: self.getSortParam().string, search: input.value }); }
         self.render();
         }, 0);
         });

         },*/

        addScrollHandler: function () {
            var win = $(window),
                aside = this.$('aside'),
                asideClone = aside.clone(),
                self = this;
            win.unbind("scroll resize");
            win.bind("scroll resize", _.throttle(function () {
                if (self.$el.offset().top > win.scrollTop()) {
                    aside.removeClass('jsFixed');
                    asideClone.remove();
                } else {
                    aside.before(asideClone);
                    aside.addClass('jsFixed');
                }
                self.updateLegend();
            }, 100));
        },

        isList: function () {
            return this.$el.hasClass('list') || this.$el.hasClass('folds');
        },

        isSingle: function () {
            return this.$el.hasClass('single');
        },

        getVisibles: function () {
            var views = _.pluck(this.displayed, 'view'),
                visibles = { first: null, last: null },
                i = 0,
                isVisible;
            for (i; i < views.length; i += 1) {
                isVisible = views[i].isVisible();
                if (visibles.first === null) {
                    visibles.first = isVisible ? (i + 1) : null;
                }
                visibles.last = isVisible ? (i + 1) : visibles.last;
                if (!isVisible && visibles.first !== null && visibles.last !== null) {
                    break;
                }
            }
            return visibles;
        },

        getVisibles2: function () {
            var views = _.pluck(this.displayed, 'view'),
                visibles = { first: null, last: null },
                i = views.length - 1,
                isVisible;
            for (i; i >= 0; i -= 1) {
                isVisible = views[i].isVisible();
                if (visibles.last === null) {
                    visibles.last = isVisible ? (i + 1) : null;
                }
                visibles.first = isVisible ? (i + 1) : visibles.first;
                if (!isVisible && visibles.first !== null && visibles.last !== null) {
                    break;
                }
            }
            return visibles;
        },

        getVisibles3: function () {
            var views = _.pluck(this.displayed, 'view').reverse(),
                visibles = { first: null, last: null },
                i = 0,
                isVisible,
                index;
            for (i; i < views.length; i += 1) {
                index = views.length - i;
                isVisible = views[i].isVisible();
                if (visibles.last === null) {
                    visibles.last = isVisible ? index : null;
                }
                visibles.first = isVisible ? index : visibles.first;
                if (!isVisible && visibles.first !== null && visibles.last !== null) {
                    break;
                }
            }
            return visibles;
        },

        updateLegend: function () {

            var visibles = this.getVisibles3(),
                old,
                total,
                legend;

            old = this.maxOnScreen;
            this.maxOnScreen = visibles.last ? visibles.last + 40 : 40;

            if (this.maxOnScreen !== old && (this.displayed.length - visibles.last < 20 || this.displayed.length - visibles.last > 60)) { // does not remove elements on scroll up. seems more efficient like that
                //console.log("re-rendering");
                this.render({ updateLegend: false });
                //return;
            }

            total = this.filteredCount !== this.collection.length ? this.filteredCount : Math.max(this.collection.expected, this.collection.length);
            legend = $('.pagination .legend');

            if (visibles.first !== null && visibles.last !== null) {
                if (total === 1) {
                    // pagination.legend.one
                    legend.text("1 result");
                } else {
                    // pagination.legend
                    legend.text("Viewing " + visibles.first + "-" + visibles.last + " of " + total + " results");
                }
            } else {
                // pagination.empty
                legend.text("");
            }

            /*if (this.collection.loader && this.collection.loader.isLoading()) {
             legend.append($("<span class='loader'></span>"));
             }*/

        }

    });

    return Collection;

});