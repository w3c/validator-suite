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