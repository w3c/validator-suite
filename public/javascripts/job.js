//Functional.install();

var Message = Backbone.Model.extend({
    
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

var Messages = Backbone.Collection.extend({
    model: Message
});

var JobData = Backbone.Model.extend({
    
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

var Job = Backbone.Model.extend({
    
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
        
    }
});

var Jobs = Backbone.Collection.extend({
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
            "name": $("header", element).text(),
            "seedUri": $("header", element).text(),
            "data": new JobData({
                "status": "-",
                "resources": 0,
                "errors": 0,
                "warnings": 0                
            }),
        });
        job.log();
        return job;
    },
    initialize: function() {

    }
});


var DashBoard = {
    Jobs: new Jobs(),
	initialize: function() {
		console.log("Jobs found:");
        console.log($("#jobs .job"));
		var jobs = _.map($("#jobs .job"), function(job) { 
        	console.log(job);
        	DashBoard.Jobs.getJobFromHTML(job); 
        });
		this.Jobs.add(jobs, {silent: true});
		this.Jobs.subscribe();
	}
};

DashBoard.initialize();