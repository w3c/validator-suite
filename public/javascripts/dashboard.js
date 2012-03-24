
window.JobView = Backbone.View.extend({
	
	tagName : "article",
	template: _.template($('#job-template').html()), // check presence first
	
	attributes: {
		"class": "job",
		"data-id": "0"
	},
	
	events: {
		"click .edit"	 : "edit",
		"click .on"		 : "putOn",
		"click .off"	 : "putOff",
		"click .stop"	 : "stop",
		"click .refresh" : "refresh",
		"click .delete"	 : "_delete"
	},
	
	initialize: function () {
		if(this.model !== undefined) {
			this.model.on('change', this.render, this);
			this.model.on('destroy', this.remove, this);
			//this.model.on('run', function () {alert("run");}, this);
			//this.model.on('stop', function () {alert("run");}, this);
		}
	},
	
	render: function () {
		this.$el.html(this.template(_.extend(this.model.toJSON(), {url : this.model.url()})));
		return this;
	},
	edit: function () {
		window.location = this.model.url() + "/edit";
		return false;
	},
	putOn: function () {
		this.model.putOn({wait:true});
		return false;
	},
	putOff: function () {
		this.model.putOff({wait:true});
		return false;
	},
	stop: function () {
		this.model.stop({wait:true});
		return false;
	},
	refresh: function () {
		this.model.refresh({wait:true});
		return false;
	},
	_delete: function () {
		this.model.destroy({wait:true});
		return false;
	},
	remove: function () {
		$(this.el).remove();
	}
	
});

window.DashboardView = Backbone.View.extend({
	el: $("#jobs"),
	jobs: new JobList(),
	initialize: function () {
		// Bind events
		this.jobs.on('add', this.addOne, this);
		this.jobs.on('reset', this.addAll, this);
		// XXX bug server-side
		var onmessage = _.bind(this._messageCallback, this);
		this.jobs.on('sync', function () {VS.Socket.reset().onmessage = onmessage;}, VS.Socket);
		// Parse the HTML to get initial data as an array of (model, view)
		_.each($("#jobs .job").toArray(), function(jobElem) { 
			this.jobs.add(Job.fromHTML(jobElem));
			$(jobElem).remove();
		}, this);
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
		VS.Socket.open().onmessage = _.bind(this._messageCallback, this);
	},
	_messageCallback: function (event) {
		console.log(event.data);
		var data = JobData.fromJSON(event.data);
		var job = this.jobs.get(data.get("jobId"));
		if (!_.isUndefined(job))
			job.syncData(data);
	}
});

$(function(){window.Dashboard = new DashboardView();});