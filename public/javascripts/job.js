$(function(){

window.LogoView = Backbone.View.extend({
	i: 0,
	el: $("#logo"),
	branches: $("#logo g"),
	rotating: false,
	initialize: function(){
		var toggle = _.bind(function(){this.toggle();}, this);
		this.$el.click(toggle);
		$("#admin").click(toggle);
	},
	rotate: function(){
		if (!this.rotating)
			return;
		this.branches.map(function(i,e){e.id = "";});
		this.branches.get(this.i%5).id = "r1";
		this.i++;
		var rotate = _.bind(function(){this.rotate();}, this);
		if (this.rotating)
			window.setTimeout(rotate, 500);
		this.el.setAttribute("class", "rotate");
	},
	start: function() {
		var rotate = _.bind(function(){this.rotate();}, this);
		this.rotating = true;
		window.setTimeout(rotate, 1);
	},
	stop: function() {
		this.el.setAttribute("class", "");
		this.branches.map(function(i,e){e.id = "";});
		this.rotating = false;
	},
	toggle: function() {
		this.rotating ? this.stop() : this.start();
	},
});

window.Logo = new LogoView();
	
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
	}
});
window.JobData.fromJSON = function(data) {
	try {
		var json = $.parseJSON(data);
		return new JobData({
			jobId: json[1],
			status: json[2],
			resources: json[3],
			oks: json[4],
			errors: json[5],
			warnings: json[6]
		});
	} catch(ex) {
		console.log(ex);
		return null;
	}
};

var methodMap = {
	   'run':'POST',
	  'stop':'POST',
	  'read':'GET',
	'create':'POST',
	'update':'POST',
	'delete':'POST'
};

window.Job = Backbone.Model.extend({
	
	defaults: {
		name: "",
		seedUri: "",
		distance: 0,
		data: new JobData(), // A collection of JobDatas containing a timestamp is what we need to build a graph
		createdAt: "",
		lastRun: "",
		lastUpdated: "",
		messages: new Messages()
	},
	
	run: function(options) {this._serverEvent('run', options);},
	
	runnow: function(options) {this._serverEvent('runnow', options);},
	
	stop: function(options) {this._serverEvent('stop', options);},
	
	_serverEvent: function(event, options) {
		options = options ? _.clone(options) : {};
		var model = this;
		var success = options.success;

		var trigger = function() {
			model.trigger(event, model, model.collection, options);
		};

		if (this.isNew()) return trigger();
		options.success = function(resp) {
			if (options.wait) trigger();
			if (success) {
				success(model, resp);
			} else {
				model.trigger('sync', model, resp, options);
			}
		};
		options.error = Backbone.wrapError(options.error, model, options);
		var xhr = (this.sync || Backbone.sync).call(this, event, this, options);
		if (!options.wait) trigger();
		return xhr;
	},
	
	sync: function(method, model, options) {
		var type = methodMap[method];
		var params = {type: type};
		if (!options.url)
			params.url = model.url() || exception("A 'url' property or function must be specified");
		if (method != 'read')
			params.data = {action: method};
		// Don't process data on a non-GET request.
		//if (params.type !== 'GET')
			//params.processData = false;
		return $.ajax(_.extend(params, options));	
	},
	
	syncData: function(jobData) {
		if (!_.isEqual(jobData.attributes, this.get('data').attributes)) {
			this.set({data: jobData});
			console.log("dataUpdated");
		}
	},
	
	log: function() {console.log(JSON.stringify(this.toJSON()));},
});

window.Job.fromHTML = function(elem) {
	console.log(parseInt($(".distance", elem).text()));
	return new Job({
		id: $(elem).attr("data-id"),
		name: $(".name", elem).text(),
		seedUri: $(".uri", elem).text(),
		distance: parseInt($(".distance", elem).text()),
		data: new JobData({
			jobId: $(elem).attr("data-id"),
			status: $(".status", elem).text(),
			resources: parseInt($(".resources", elem).text()),
			oks: parseInt($(".oks", elem).text()),
			errors: parseInt($(".errors", elem).text()),
			warnings: parseInt($(".warnings", elem).text())				
		})
	});
};

window.JobView = Backbone.View.extend({
	
	tagName : "article",
	template: _.template($('#job-template').html()),
	
	attributes: {
		"class": "job",
		"data-id": "0"
	},
	
	events: {
		"click .edit"	 : "edit",
		"click .run"	 : "run",
		"click .runnow"	 : "runnow",
		"click .stop"	 : "stop",
		"click .delete"	: "_delete"
	},
	
	initialize: function() {
		if(this.model !== undefined) {
			this.model.bind('change', this.render, this);
			this.model.bind('destroy', this.remove, this);
			//this.model.bind('run', function(){alert("run");}, this);
			//this.model.bind('stop', function(){alert("run");}, this);
		}
	},
	
	render: function() {
		this.$el.html(this.template(this.model.toJSON()));
		return this;
	},
	edit: function() {
		window.location = "/job/" + this.model.id + "/edit";
		return false;
	},
	run: function() {
		this.model.run();
		return false;
	},
	runnow: function() {
		this.model.runnow();
		return false;
	},
	stop: function() {
		this.model.stop();
		return false;
	},
	_delete: function() {
		this.model.destroy({wait:true});
		return false;
	},
	remove: function () {
		$(this.el).remove();
	}
	
});

window.JobList = Backbone.Collection.extend({
	url: '/job',
	model: Job,
});

window.DashBoardView = Backbone.View.extend({
	el: $("#jobs > div"),
	jobs: new JobList(),
	
	initialize: function() {
		// Bind events
		this.jobs.on('add', this.addOne, this);
		this.jobs.on('reset', this.addAll, this);
		// XXX bug server-side
		this.jobs.on('run', VS.Socket.reset);
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
			//console.log(event.data);
			var data = JobData.fromJSON(event.data);
			var job = DashBoard.jobs.get(data.get("jobId"));
			if (!_.isUndefined(job))
				job.syncData(data);
		},
		
		open: function() {
			var s = VS.Socket;
			if (s.ws == null
				|| s.ws.readyState === s.type.CLOSING 
				|| s.ws.readyState === s.type.CLOSED) { 
				s.ws = new s.type(s.url);
				s.ws.onmessage = s.onmessage;
			}
			return this.ws;
		},
		
		reset: function() {
			if (VS.Socket.ws == null)
				return;
			VS.Socket.ws.close();
			VS.Socket.open();
		}
	},

	exception: function(msg) {
		throw new Error(msg);
	}
};

window.DashBoard = new DashBoardView();

});