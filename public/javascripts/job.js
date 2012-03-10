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
		console.log(data);
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
	fromHTML: function(elem) {
		this.set({
			// TODO replace by pure DOM api?
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
		return this;
	}
});

window.JobView = Backbone.View.extend({
	tagName:  "article",
	attributes: {
		"class": "job",
		"data-id": "0"
	},
	events: {
		"click .edit"	 : "edit",
		"click .run"	 : "run",
		"click .stop"	 : "stop",
		"click .delete"  : "_delete"
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
	edit:   function() {
		window.location = "/job/" + this.model.id + "/edit";
		return false;
	},
	run:    function() {
		$.ajax({
			type: "POST",
			url: "/job/" + this.model.id,
			data: {action: "run"},
			// TODO see why this is necessary. loss of subscribers on start?
			success: function() {VS.Socket.reset();}
		});
		return false;
	},
	stop:   function() {
		$.ajax({
			type: "POST",
			url: "/job/" + this.model.id,
			data: {action: "stop"},
		});
		return false;
	},
	_delete:  function() {
		$.ajax({
			type: "POST",
			url: "/job/" + this.model.id,
			data: {action: "delete"}
			//success: this.clear
		});
		return false;
	},
	remove: function() {this.$el.remove();},
	clear:  function() {this.model.destroy();}
});

window.JobList = Backbone.Collection.extend({
	model: Job,
});

window.DashBoardView = Backbone.View.extend({
	el: $("#jobs"),
	jobs: new JobList(),
	
	initialize: function() {
		// Bind events
		this.jobs.on('add', this.addOne, this);
		this.jobs.on('reset', this.addAll, this);
		// Parse the HTML to get initial data as an array of (model, view)
		_.each($("#jobs .job").toArray(), function(jobElem) { 
			this.jobs.add((new Job()).fromHTML(jobElem));
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
	subscribe: function() {
		VS.Socket.open();
	}
});

window.VS = {
	
	Socket: {
		ws: null,
		url: "ws://" + window.location.host + "/jobs",
		type: window['MozWebSocket'] ? MozWebSocket : WebSocket,
		onmessage: function(event) {
			var data = (new JobData()).fromJson(event.data);
			var job = DashBoard.jobs.get(data.get("jobId"));
			if (typeof job !== 'undefined')
				job.set("data", data);
		},
		open: function() {
			if (this.ws == null || this.ws.readyState === this.type.CLOSING || this.ws.readyState === this.type.CLOSED) { 
				this.ws = new this.type(this.url);
				this.ws.onmessage = this.onmessage;
			}
			return this.ws;
		},
		reset: function() {
			if (this.ws == null) 
				return;
			this.ws.close();
			this.open();
		}
	}
	
};

window.DashBoard = new DashBoardView();

});