/*window.LogoView = Backbone.View.extend({
	i: 0,
	el: $("#logo"),
	branches: $("#logo g"),
	rotating: false,
	initialize: function () {
		var toggle = _.bind(function () {this.toggle();}, this);
		this.$el.click(toggle);
		$("#admin").click(toggle);
	},
	rotate: function () {
		if (!this.rotating)
			return;
		this.branches.map(function(i,e){e.id = "";});
		this.branches.get(this.i%5).id = "r1";
		this.i++;
		var rotate = _.bind(function () {this.rotate();}, this);
		if (this.rotating)
			window.setTimeout(rotate, 500);
		this.el.setAttribute("class", "rotate");
	},
	start: function () {
		var rotate = _.bind(function () {this.rotate();}, this);
		this.rotating = true;
		window.setTimeout(rotate, 1);
	},
	stop: function () {
		this.el.setAttribute("class", "");
		this.branches.map(function(i,e){e.id = "";});
		this.rotating = false;
	},
	toggle: function () {
		this.rotating ? this.stop() : this.start();
	},
});

$(function () {window.Logo = new LogoView();});*/