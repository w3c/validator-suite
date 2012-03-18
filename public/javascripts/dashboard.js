$(function () {

window.LogoView = Backbone.View.extend({
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
		activity: "",
		mode: "",
		resources: 0,
		oks: 0,
		errors: 0,
		warnings: 0
	}
});
window.JobData.fromJSON = function (data) {
	try {
		var json = $.parseJSON(data);
		return new JobData({
			jobId: json[1],
			activity: json[2],
			mode: json[3],
			resources: json[4],
			oks: json[5],
			errors: json[6],
			warnings: json[7]
		});
	} catch(ex) {
		console.log(ex);
		return null;
	}
};

var methodMap = {
	    'on':'POST',
	   'off':'POST',
	  'stop':'POST',
   'refresh':'POST',
	'create':'POST',
	  'read':'GET',
	'update':'POST',
	'delete':'POST',
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
	
	putOn: function(options) {this._serverEvent('on', options);},
	
	putOff: function(options) {this._serverEvent('off', options);},
	
	stop: function(options) {this._serverEvent('stop', options);},
	
	refresh: function(options) {this._serverEvent('refresh', options);},
		
	_serverEvent: function(event, options) {
		options = options ? _.clone(options) : {};
		var model = this;
		var success = options.success;

		var trigger = function () {
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
	
	log: function () {console.log(JSON.stringify(this.toJSON()));},
});

window.Job.fromHTML = function(elem) {
	return new Job({
		id: $(elem).attr("data-id"),
		name: $(".name", elem).text(),
		seedUri: $(".uri", elem).text(),
		distance: parseInt($(".distance", elem).text()),
		data: new JobData({
			jobId: $(elem).attr("data-id"),
			activity: $(".activity", elem).text(),
			mode: $(".mode", elem).text(),
			resources: parseInt($(".resources", elem).text()),
			oks: parseInt($(".oks", elem).text()),
			errors: parseInt($(".errors", elem).text()),
			warnings: parseInt($(".warnings", elem).text())				
		})
	});
};

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

window.JobList = Backbone.Collection.extend({
	url: '/job',
	model: Job,
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

window.VS = {
	
	Socket: {
		
		url: "ws://" + window.location.host + "/jobs",
		type: window['MozWebSocket'] ? MozWebSocket : WebSocket,
		
		open: function (options) {
			if (typeof this.ws != 'object'
				|| this.ws.readyState === this.type.CLOSING 
				|| this.ws.readyState === this.type.CLOSED) {
				var url = options && options.url ?
						"ws://" + window.location.host + options.url
						: this.url;
				this.ws = new this.type(url);
			}
			return this.ws;
		},
		
		reset: function () {
			if (typeof this.ws != 'object')
				return;
			this.ws.close();
			return this.open();
		}
	},

	exception: function(msg) {
		throw new Error(msg);
	}
};

});