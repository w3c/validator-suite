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

window.MessageList = Backbone.Collection.extend({
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

window.Job = Backbone.Model.extend({
	
	defaults: {
		name: "",
		seedUri: "",
		distance: 0,
		maxResources: 0,
		health: 0,
		data: new JobData(), // A collection of JobDatas containing a timestamp is what we need to build a graph
		createdAt: "",
		lastRun: "",
		lastUpdated: "",
		messages: new MessageList()
	},
	
	methodMap: {
		    'on':'POST',
		   'off':'POST',
		  'stop':'POST',
	   'refresh':'POST',
		'create':'POST',
		  'read':'GET',
		'update':'POST',
		'delete':'POST',
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
		seedUri: $(".url", elem).text(),
		distance: parseInt($(".distance", elem).text()),
		data: new JobData({
			activity: $(".status span", elem).text(),
			//mode: $(".mode", elem).text(),
			errors: parseInt($(".errors", elem).text()),
			warnings: parseInt($(".warnings", elem).text()),			
			resources: parseInt($(".resources", elem).text())
		}),
		maxResources: parseInt($(".maxResources", elem).text()),
		health: parseInt($(".health progress", elem).attr("value"))
	});
};

window.JobList = Backbone.Collection.extend({
	url: '/jobs',
	model: Job,
});