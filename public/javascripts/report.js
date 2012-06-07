window.URLArticleView = Backbone.View.extend({
	
	tagName : "article",
	template: _.template($('#urlArticle-template').html()), // check presence first
	
	initialize: function () {
		if(this.model !== undefined) {
			//this.attributes["jobId"] = this.model.id;
			this.model.on('change', this.render, this);
			this.model.on('destroy', this.remove, this);
			//this.model.on('run', function () {alert("run");}, this);
			//this.model.on('stop', function () {alert("run");}, this);
		}
	},
	
	render: function () {
		this.$el.html(this.template(_.extend(this.model.toJSON())));
		return this;
	},
	
});


window.ReportView = Backbone.View.extend({
	el: $("#urlArticles"),
	articles: new URLArticleList(),
	
	initialize: function () {
		// Bind events
		this.articles.on('add', this.addAll, this);
		this.articles.on('reset', this.addAll, this);
		this.id = $("#urlArticles").attr("data-id");
		//this.subscribe();
	},
	addAll: function () {
		var views = this.articles.map(function (article) {
			return (new URLArticleView({model: article})).render().el;
		}, this);
		$("article", this.$el).remove();
		_.each(views, function (view) {this.$el.append(view);}, this);
	},
//	subscribe: function () {
//		VS.Socket.open({url: "/jobs/ws/" + this.id}).onmessage = _.bind(this._messageCallback, this);
//	},
//	_messageCallback: function (event) {
//		console.log(event.data);
//		try {
//			var json = $.parseJSON(event.data);
//			var type = json[0];
//			if (type === "Resource") {
//				var data = {
//					resourceId: json[1],
//					url: json[2]
//				};
//				if (this.articles.where({url: data.url}).length < 1) {
//					this.articles.add(new URLArticle({
//						url: data.url,
//						timestamp: "Never",
//					}));
//				} else {
//					console.log("Resource already present on the page: " + data.url);
//				}
//			} else if (type === "Assertions") {
//				var assertions = json[1];
//				_.each(assertions, function (assertion) {
//					var url = assertion[0];
//					var timestamp = assertion[1];
//					var warnings = assertion[2];
//					var errors = assertion[3];
//					_.map(this.articles.where({url: url}), function (article) {
//						article.set({
//							timestamp: timestamp,
//							warnings: article.get("warnings") + warnings,
//							errors: article.get("errors") + errors,
//						});
//					});
//					this.articles.sort();
//				}, this);
//			}
//		} catch(ex) {
//			console.log(ex);
//			return null;
//		}
//	}
});

$(function () {
	//window.Report = new ReportView();
//	var a = $("#report article");
//	$("body").removeClass("no-js").addClass("js");
//	a.each(function (i, article) {
//		var ar = $(article);
//		ar.addClass("folded");
//		// TODO implement that in the template
//		if ($("article > div:first-child", ar).length == 0 && $(".messages", ar).length == 0)
//			ar.removeClass("folded").addClass("empty");
//	});
//	$("#report article > :first-child").click(
//		function (e){
//			e.preventDefault(); 
//			$(this).parent().toggleClass("folded");
//			return false;
//		}
//	);
//	$(".compact").click(
//		function (e){
//			e.preventDefault(); 
//			a.toggleClass("compact");
//			return false;
//		}
//	);
	
//	var setHash = function(hash) {
//		// Make sure that the hash is the first character, and extract from (presumably) full URL if not
//		if (hash.indexOf('#') > 0) {
//			hash = hash.substr(hash.lastIndexOf('#'));
//		} else if (hash.substr(0, 1) !== '#') {
//			hash = '#' + hash;
//		}
//		// Add our hash element to the history/URL
//		if (window.history.pushState) {
//			window.history.pushState(null, null, hash);
//		} else {
//			window.location = hash;
//		}
//	};
//
//	//$("a.resource").click(function (e){e.preventDefault(); setHash($(this).attr('href'));});
});