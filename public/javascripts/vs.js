window.VS = {
	
	Socket: {
		
		url: "ws://" + window.location.host + "/jobs/ws",
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
			this.ws.onmessage = this._onmessage;
			return this.ws;
		},
		
		reset: function (options) {
			if (typeof this.ws != 'object')
				return;
			this.ws.close();
			return this.open(options);
		},
		
		_onmessage: function(event) {
			console.log(event.data);
			//try {
				var json = $.parseJSON(event.data);
				var type = json[0];
				if (type === "Dashboard" && Dashboard !== undefined) {
					var data = DashboardUpdate.fromJSON(json);
					var job = Dashboard.jobs.get(data.get("jobId"));
					if (!_.isUndefined(job))
						job.syncData(data);
				} /*else if (type === "Resource" && Report !== undefined) {
					var data = {
						resourceId: json[1],
						url: json[2]
					};
					if (Report.articles.where({url: data.url}).length < 1) {
						Report.articles.add(new URLArticle({
							url: data.url,
							timestamp: "Never",
						}));
					} else {
						console.log("Resource already present on the page: " + data.url);
					}
				}*/ else if (type === "Assertions" && Report !== undefined) {
					var assertions = json[1];
					_.each(assertions, function (assertion) {
						var url = assertion[0];
						var timestamp = assertion[1];
						var warnings = assertion[2];
						var errors = assertion[3];
						var current = Report.articles.where({url: url});
						if (current.length < 1) {
							Report.articles.add(new URLArticle({
								url: url,
								timestamp: timestamp,
								warnings: warnings,
								errors: errors,
							}), true); // silent
						} else {
							_.map(current, function (article) {
								article.set({
									timestamp: timestamp,
									warnings: article.get("warnings") + warnings,
									errors: article.get("errors") + errors,
								});
							});
						}
						Report.articles.sort();
					});
				}
//			} catch(ex) {
//				console.log(ex);
//				return null;
//			}
		}
	},

	exception: function(msg) {
		throw new Error(msg);
	}
};