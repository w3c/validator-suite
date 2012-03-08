$(function(){
	
window.Message = Backbone.Model.extend({
    
    url: "",
    
    defaults: {
        "id": "",
        "type": 0,
        "timestamp": "",
        "assertorId": "",
        "line": 0,
        "column": 0,
        "context": "",
        "message": ""
    },
    
    initialize: function() {
        
    }
});

window.Messages = Backbone.Collection.extend({
    model: Message
});

window.JobData = Backbone.Model.extend({
    
    defaults: {
        "status": "-",
        "resources": 0,
        "oks": 0,
        "errors": 0,
        "warnings": 0
    },
    
    initialize: function() {
        
    }
});

window.Job = Backbone.Model.extend({
    
    defaults: {
        "id": "",
        "name": "",
        "seedUri": "",
        "distance": "",
        "data": new JobData(), // A collection of JobDatas containing a timestamp is what we need to build a graph
        "createdAt": "",
        "lastRun": "",
        "lastUpdated": "",
        "messages": new Messages()
    },
    
    toJson: function() {
    	return JSON.stringify(this.toJSON());
    },
    
    log: function() {
    	console.log(this.toJson());
    },
    
    initialize: function() {
    	console.log("New Job");
    	this.log();
    }
});

window.JobView = Backbone.View.extend({

    tagName:  "article",
    
    attributes: {"class" :"job"},

    template: _.template($('#job-template').html()),

    events: {
      "click .edit"     : "edit",
      "click .delete"   : "clear",
      "click .run"      : "run",
      "click .stop"     : "stop"
    },

    initialize: function() {
      this.model.bind('change', this.render, this);
      console.log("New JobView");
      this.model.bind('destroy', this.remove, this);
    },

    render: function() {
      this.$el.html(this.template(this.model.toJSON()));
      return this;
    },

    edit: function() {
      
    },

    run: function() {
      
    },

    stop: function(e) {
      
    },

    remove: function() {
      this.$el.remove();
    },

    clear: function() {
      this.model.destroy();
    }

});

window.JobList = Backbone.Collection.extend({
	
    model: Job,
    
    subscribe: function() {
        //var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket;
        //WS = new WS("ws://");
        //WS.onmessage = function(event) {
        //    Jobs.updateData(JobData.fromJSON(event.data));
        //};
    },
    
    getJobFromHTML: function(element) {
        var job = new Job({
            "name": $(".name", element).text(),
            "seedUri": $(".uri", element).text(),
            "data": new JobData({
                "status": $(".status", element).text(),
                "resources": parseInt($(".resources", element).text()),
                "errors": parseInt($(".errors", element).text()),
                "warnings": parseInt($(".warnings", element).text())                
            }),
        });
        return job;
    },
    
    initialize: function() {
    	console.log("New JobList");
    }
});

window.Jobs = new JobList();

window.DashBoardView = Backbone.View.extend({
	el: $("#jobs"),
	
    events: {
    	
    },
    
	initialize: function() {
		console.log("New DashBoard");
		console.log("Jobs found:");
        console.log($("#jobs .job"));
		
        // Bind events
	    Jobs.on('add', this.addOne, this);
	    Jobs.on('reset', this.addAll, this);
	    //Jobs.on('all', this.render, this);
        
        // Parse the HTML to get initial data
        Jobs.reset();
        var jobs = $("#jobs .job").map(function(job) { 
        	return Jobs.getJobFromHTML(job); 
        });
        Jobs.add(jobs.toArray(), {silent: true});
        //Jobs.add(jobs.toArray());
		
		// Subscribe to job updates through a WebSocket
		Jobs.subscribe();
	},
	
    render: function() {
    	console.log("render");
    },
    
    addOne: function(job) {
    	console.log("addOne");
    	console.log(job);
    	var view = new JobView({model: job});
    	this.$el.append(view.render().el);
    },
    
    addAll: function() {
    	Jobs.each(this.addOne);
    }
});

window.DashBoard = new DashBoardView;

});