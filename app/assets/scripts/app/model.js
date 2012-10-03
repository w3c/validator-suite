/*window.Message = Backbone.Model.extend({
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

window.MessageList = Backbone.Collection.extend({
	model: Message
});

window.DashboardUpdate = Backbone.Model.extend({
	defaults: {
		activity: "",
		resources: 0,
		errors: 0,
		warnings: 0,
		health: 0,
		lastCompleted: "Never"
	}
});

window.DashboardUpdate.fromJSON = function (json) {
	try {
		return new DashboardUpdate({
			jobId: json[1],
			activity: json[2],
			resources: json[3],
			errors: json[4],
			warnings: json[5],
			health: json[6],
			lastCompleted: json[7] 
		});
	} catch(ex) {
		console.log(ex);
		return null;
	}
};

window.Job = Backbone.Model.extend({
	
	defaults: {
		name: "",
		seedUri: "",
		maxResources: 0,
		data: new DashboardUpdate(), // A collection of DashboardUpdates containing a timestamp is what we need to build a graph
		createdAt: "",
		lastRun: "",
		messages: new MessageList()
	},
	
	methodMap: {
		    'on':'POST',
		   'off':'POST',
		  'stop':'POST',
	       'run':'POST',
		'create':'POST',
		  'read':'GET',
		'update':'POST',
		'delete':'POST',
	},
	
	putOn: function(options) {this._serverEvent('on', options);},
	
	putOff: function(options) {this._serverEvent('off', options);},
	
	stop: function(options) {this._serverEvent('stop', options);},
	
	run: function(options) {this._serverEvent('run', options);},
		
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
		var type = this.methodMap[method];
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
		if (jobData.get("lastCompleted") == "Never")
			jobData.set("lastCompleted", this.get("data").get("lastCompleted"));
		this.set({data: jobData});
	},
	
	log: function () {console.log(JSON.stringify(this.toJSON()));},
});

window.Job.fromHTML = function(elem) {
	return new Job({
		id: $(elem).attr("data-id"),
		name: $(".name", elem).text(),
		seedUri: $(".url", elem).text(),
		data: new DashboardUpdate({
			activity: $(".status", elem).text(),
			resources: parseInt($(".resources", elem).text()),
			errors: parseInt($(".errors", elem).text()),
			warnings: parseInt($(".warnings", elem).text()),			
			health: parseInt($(".health progress", elem).attr("value")),
			lastCompleted: $(".completed", elem).text()
		}),
		maxResources: parseInt($(".maxResources", elem).text()),
		health: parseInt($(".health progress", elem).attr("value"))
	});
};

window.JobList = Backbone.Collection.extend({
	url: '/jobs',
	model: Job,
});

window.URLArticle = Backbone.Model.extend({
	url: "",
	defaults: {
		timestamp: "",
		warnings: 0,
		errors: 0
	}
});

window.URLArticleList = Backbone.Collection.extend({
	model: URLArticle,
	comparator: function (article1, article2) {
		if (article1.get("errors") > article2.get("errors")) {
			return -1;
		} else if (article1.get("errors") === article2.get("errors")) {
			if (article1.get("url") < article2.get("url"))
				return -1;
			else
				return +1;
		} else {
			return +1;
		}
	}
});

window.URLArticle.fromHTML = function(elem) {
	return new URLArticle({
		url: $(".url", elem).text(),
		timestamp: $(".timestamp", elem).text(),
		warnings: parseInt($(".warnings", elem).text()),
		errors: parseInt($(".errors", elem).text())
	});
};*/