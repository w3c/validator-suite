var Job = Backbone.Model.extend({
	
	defaults: {
		"id": "",
		"name": "",
		"seedUri": "",
		"distance": "",
		"status": "",
		"lastRun": "",
		"lastUpdated": "",
		"errors": 0,
		"warnings": 0,
		"messages": new Messages()
	},
	
	initialize: function() {
		
	}
	
});

Functional.install();

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

var Jobs = Backbone.Collection.extend({
	model: Job
});

var Messages = Backbone.Collection.extend({
	model: Message
});

var t = new Job();

console.log(t.name);
console.log(t.get("name"));