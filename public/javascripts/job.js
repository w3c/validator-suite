$(function(){
	
window.Message = Backbone.Model.extend({
	url: "",
	defaults: {
		type: 0,
		timestamp: "",
		assertorId: "",
		line: 0,
		column: 0,
		context: "",
		message: ""
	}
});

window.Messages = Backbone.Collection.extend({
	model: Message
});

window.JobData = Backbone.Model.extend({
	defaults: {
		status: "-",
		resources: 0,
		oks: 0,
		errors: 0,
		warnings: 0
	},
	fromJson: function(data) {
		try {
			var json = $.parseJSON(data);
			this.set({
				jobId: json[1],
				status: json[2],
			    resources: json[3],
			    oks: json[4],
			    errors: json[5],
			    warnings: json[6]
			});
			return this;
		} catch(e) {
			return null;
		}
	}
});

window.Job = Backbone.Model.extend({
	defaults: {
		name: "",
		seedUri: "",
		distance: "",
		data: new JobData(), // A collection of JobDatas containing a timestamp is what we need to build a graph
		createdAt: "",
		lastRun: "",
		lastUpdated: "",
		messages: new Messages()
	},
	log:    function() {console.log(this.toJson());},
	toJson: function() {return JSON.stringify(this.toJSON());},
});

window.JobView = Backbone.View.extend({
	tagName:  "article",
	attributes: {
		"class": "job",
		"data-id": "0"
	},
	events: {
		"click .edits"	 : "edit",
		"click .deletes"  : "clear",
		"click .runs"	 : "run",
		"click .stops"	 : "stop"
	},
	template: _.template($('#job-template').html()),
	
	initialize: function() {
		if(this.model !== undefined) {
			this.model.bind('change', this.render, this);
			this.model.bind('destroy', this.remove, this);
		}
	},
	render: function() {
		this.$el.html(this.template(this.model.toJSON()));
		return this;
	},
	edit:   function() {},
	run:    function() {},
	stop:   function() {},
	remove: function() {this.$el.remove();},
	clear:  function() {this.model.destroy();},
	fromHTML: function(elem) {
		this.setElement(elem);
		this.model = new Job({
			id: $(elem).attr("data-id"),
			name: $(".name", elem).text(),
			seedUri: $(".uri", elem).text(),
			data: new JobData({
				status: $(".status", elem).text(),
				resources: parseInt($(".resources", elem).text()),
				errors: parseInt($(".errors", elem).text()),
				warnings: parseInt($(".warnings", elem).text())				
			})
		});
		this.initialize();
		return this;
	}
});

window.JobList = Backbone.Collection.extend({
	model: Job,
});

window.DashBoardView = Backbone.View.extend({
	el: $("#jobs"),
	jobs: new JobList(),
	jobViews: [],
	
	initialize: function() {
		// Bind events
		this.jobs.on('add', this.addOne, this);
		this.jobs.on('reset', this.addAll, this);
		//this.jobs.on('all', this.render, this);
		
		// Parse the HTML to get initial data as an array of (model, view)
		this.jobs.reset();
		var views = $("#jobs .job").map(function(index, jobElem) { 
			return (new JobView()).fromHTML(jobElem);
		}).toArray();
		var models = _.map(views, function(view){return view.model;});
		
		// Add the jobs to the collection silently and register the views with the Dashboard.
		this.jobs.add(models, {silent: true});
		this.jobViews = _.union(this.jobViews, views);
		
		// Subscribe to job updates through a WebSocket
		this.subscribe();
	},
	render: function() {
		this.$el.empty();
		this.$el.append(_.map(this.jobViews, function(view){return view.render().el;}));
	},
	addOne: function(job) {
		this.jobViews.push(new JobView({model: job}));
		this.render();
	},
	addAll: function() {
		this.jobs.each(this.addOne);
	},
	subscribe: function() {
		var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket;
		WS = new WS("ws://localhost:9000/jobs");
		WS.onmessage = function(event) {
			var data = (new JobData()).fromJson(event.data);
			var job = DashBoard.jobs.get(data.get("jobId"));
			job.set("data", data);
		};
	},
});

window.DashBoard = new DashBoardView;

});