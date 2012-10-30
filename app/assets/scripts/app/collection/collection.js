define(["lib/Logger", "libs/backbone", "lib/Util", "lib/Loader"], function (Logger, Backbone, Util, Loader) {

    "use strict";

    var logger = new Logger("Collection"),
        getComparatorBy,
        Collection;

    getComparatorBy = function (param, reverse) {

        reverse = (reverse || false);

        if (param === "lastValidated" || param === "completedOn") {
            return function (o1, o2) {
                var p1 = o1.get(param),
                    p2 = o2.get(param);
                if (p1 === null && p2 === null) { return 0; }
                if (p2 === null) { return reverse ? -1 : +1; }
                if (p1 === null) { return reverse ? +1 : -1; }
                if (p1.timestamp > p2.timestamp) { return reverse ? -1 : +1; }
                if (p1.timestamp === p2.timestamp) { return 0; }
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
    };

    Collection = Backbone.Collection.extend({

        //comparator: getComparatorBy("id"),

        sortByParam: function (param, reverse, options) {
            this.comparator = getComparatorBy(param, reverse);
            //if (_.isUndefined(silent) || !silent)
            this.sort(options);
        },

        isComplete: function () {
            return this.expected && this.length < this.expected ? false : true;
        },

        initialize: function () {
            this.on('add reset', function () {
                if (this.expected && this.expected < this.length) {
                    this.expected = this.length;
                }
            });
            if (_.isFunction(this.init)) { this.init(); }
        },

        configure: function (options) {
            options = this.options = (options || {});
            this.view(options);
            if (options.load || (_.isUndefined(options.load) && this._view.isList())) {
                this.load();
            }
            return this;
        },

        view: function (options) {
            if (this._view) { return this._view; }
            this._view = new this.constructor.prototype.constructor.View(_.extend({ collection: this }, options));
            return this._view;
        },

        load: function () {
            var loader = this.loader = new Loader(this),
                view = this.view();
            loader.start({
                sort: view.getSortParam().string
                //search: view.getSearchParam()
            });
            //loader.on('update', view.render, view);
        }

    });

    Collection.View = Backbone.View.extend({

        displayed: [],

        maxOnScreen: 30,

        filteredCount: 0,

        sortParams: [],

        search: function (search) {
            this.search_ = function (model) {
                return model.search(search);
            };
            if (this.collection.loader) {
                this.collection.loader.setData({ search: search });
            }
            this.render();
        },

        initialize: function () {

            var collection = this.collection,
                initial_sort = this.getSortParam();

            if (this.templateId && !this.template) {
                this.template = Util.getTemplate(this.templateId);
            }

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

            collection.on('add', this.render, this);
            collection.on('reset', this.render, this);
            collection.on('destroy', this.render, this);

            this.addSortHandler();
            //this.addSearchHandler();

            if (this.isList()) {
                this.addScrollHandler();
            }

            if (_.isUndefined(this.options.loadFromMarkup) || this.options.loadFromMarkup) {
                logger.log("loadFromMarkup");
                this.loadFromMarkup();
            }

            if (!_.isFunction(collection.comparator)) {
                collection.sortByParam(initial_sort.param, initial_sort.reverse, { silent: true });
            } else {
                //collection.sort({ silent: true });
            }

            if (_.isFunction(this.init)) { this.init(); }

        },

        loadFromMarkup: function () {
            var collection = this.collection,
                models;

            if (!_.isFunction(collection.model.fromHtml)) {
                logger.error("fromHtml function not provided");
                return false;
            }

            models = this.$('article').map(function (i, article) {
                var $article = $(article),
                    value = Util.valueFrom($article);
                return new collection.model.fromHtml($(article));
            }).toArray();

            logger.info("parsed " + models.length + " model(s) from the page.");

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

        render: function () {

            logger.log("render");

            if (_.isFunction(this.beforeRender)) { this.beforeRender(); }

            var models = this.collection.models,
                elements,
                empty;

            if (_.isFunction(this.search_)) {
                models = _.filter(models, this.search_);
            }

            if (_.isFunction(this.filter)) {
                models = _.filter(models, this.filter);
            }

            this.filteredCount = models.length;

            models = this.displayed = models.slice(0, this.maxOnScreen);

            elements = models.map(function (model) {
                return model.view({ template: this.template }).render().el;
            }, this);

            this.$('.empty').remove();

            this.$el.children('article').detach();

            if (elements.length > 0) {
                _.map(elements, function (elem) {
                    this.$el.append(elem);
                    this.$el.append(" ");
                }, this);
            } else {
                empty = $('<p class="empty"></p>');
                this.$el.append(empty);
                console.log(this.collection.expected === 0);
                if (this.collection.expected === 0) {
                    if (_.isFunction(this.emptyMessage)) {
                        empty.html(this.emptyMessage());
                    } else if (_.isString(this.emptyMessage)) {
                        empty.html(this.emptyMessage);
                    } else {
                        logger.warn("No emptyMessage function or value provided");
                    }
                    //empty.html("No jobs have been configured yet. <a href='" + this.collection.url + "/new" + "'>Create your first job.</a>");
                } else if (this.collection.loader && this.collection.loader.isSearching()) {
                    empty.html("<span class='loader'></span>");
                } else if (this.collection.loader && !this.collection.loader.isSearching()) {
                    empty.text("No search result.");
                }
            }

            //if (this.isList()) { setTimeout(_.bind(this.updateLegend, this), 0); }
            if (this.isList()) { this.updateLegend(); }

            if (_.isFunction(this.afterRender)) { this.afterRender(); }

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
                    if (self.loader) { self.loader.setData({ sort: "-" + param/*, search: self.getSearchParam()*/ }, true); }
                    self.collection.sortByParam(param);
                    return false;
                });
                descend.click(function (event) {
                    event.preventDefault();
                    sortLinks.removeClass("current");
                    $(this).addClass("current");
                    if (self.loader) { self.loader.setData({ sort: param, search: self.getSearchParam() }, true); }
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
            win.bind("scroll resize", function () {
                setTimeout(function () {
                    if (self.$el.offset().top > win.scrollTop()) {
                        aside.removeClass('jsFixed');
                        asideClone.remove();
                    } else {
                        aside.before(asideClone);
                        aside.addClass('jsFixed');
                    }
                    self.updateLegend();
                }, 0);
            });
            //win.scroll();
        },

        isList: function () {
            return this.$el.hasClass('list') || this.$el.hasClass('folds');
        },

        getVisibles: function () {
            var views = _.invoke(this.displayed, 'view'),
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
            var views = _.invoke(this.displayed, 'view'),
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
            var views = _.invoke(this.displayed, 'view').reverse(),
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

            var visibles = this.getVisibles3(), /*{ first: null, last: null }, */
                old,
                total,
                legend;

            /*var views = _.invoke(this.displayed, 'view'),
                visibles = { first: null, last: null },
                i = 0,
                isVisible,
                old,
                total,
                legend;
            for (i; i < views.length; i += 1) {
                isVisible = views[i].isVisible();
                if (visibles.first === null) {
                    visibles.first = isVisible ? (i + 1) : null;
                }
                visibles.last = isVisible ? (i + 1) : visibles.last;
                if (!isVisible && visibles.first !== null && visibles.last !== null) {
                    break;
                }
            }*/

            old = this.maxOnScreen;
            this.maxOnScreen = visibles.last && visibles.last >= 30 ? visibles.last + 10 : 30;
            //this.maxOnScreen = 200;

            if (this.maxOnScreen !== old && visibles.last === this.displayed.length) { // does not remove elements on scroll up. seems more efficient like that
                this.render();
            }

            total = this.filteredCount; //this.getSearchParam() !== "" ? this.filteredCount : this.collection.expected;

            legend = $('.pagination .legend');

            if (visibles.first !== null && visibles.last !== null) {
                //legend.text("Viewing " + visibles.first + "-" + visibles.last +
                //    " of " + total + " results (" + this.collection.length + "/" + this.collection.expected + " - " + this.maxOnScreen + ")");
                if (total === 1) {
                    legend.text("1 result"); // pagination.legend.one
                } else {
                    legend.text("Viewing " + visibles.first + "-" + visibles.last + " of " + total + " results"); // pagination.legend
                }
            } else {
                legend.text(""); // pagination.empty
            }

        }

    });

    return Collection;

});