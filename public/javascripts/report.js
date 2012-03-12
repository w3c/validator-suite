$(function () {
		
window.ReportView = Backbone.View.extend({
	el: $("#report > div"),
	messages: new MessageList(),
	
	initialize: function () {
		// Bind events
		this.messages.on('add', this.addOne, this);
		this.messages.on('reset', this.addAll, this);
		// Subscribe to job updates through a WebSocket
		this.subscribe();
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
		VS.Socket.open("/job/" + this.$el.text() + "/socket");
	}
});

window.Report = new window.ReportView();
	
	
});