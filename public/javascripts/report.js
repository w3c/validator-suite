
window.ReportView = Backbone.View.extend({
	el: $("#report > div"),
	messages: new MessageList(),
	
	initialize: function () {
		// Bind events
		this.messages.on('add', this.addOne, this);
		this.messages.on('reset', this.addAll, this);
		// Subscribe to job updates through a WebSocket
		//this.subscribe();
	},
	addOne: function(job) {
		var view = new JobView({model: job});
		this.$el.append(view.render().el);
	},
	addAll: function(j) {
		this.$el.empty();
		this.jobs.each(this.addOne, this);
	},
	subscribe: function () {
		//VS.Socket.open("/job/" + this.$el.text() + "/socket");
	}
});

$(function () {
	window.Report = new ReportView();
	var a = $("#report article");
	$("body").removeClass("no-js").addClass("js");
	a.each(function (i, article) {
		var ar = $(article);
		ar.addClass("folded");
		// TODO implement that in the template
		if ($("article > div:first-child", ar).length == 0 && $(".messages", ar).length == 0)
			ar.removeClass("folded").addClass("empty");
	});
	$("#report article > :first-child").click(
		function (e){
			e.preventDefault(); 
			$(this).parent().toggleClass("folded");
			return false;
		}
	);
	$(".compact").click(
		function (e){
			e.preventDefault(); 
			a.toggleClass("compact");
			return false;
		}
	);
	
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