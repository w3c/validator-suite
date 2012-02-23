var Job = Backbone.Model.extend({
	
	defaults: {
		"name": "",
		"seeUri": "",
		"status": "",
		"lastRun": "",
		"lastUpdated": "",
		"errors": 0,
		"warnings": 0
	},
	
	initialize: function() {
		
	}
	
});

var t = new Job();

console.log(t.name);
console.log(t.get("name"));