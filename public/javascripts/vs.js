//todo 	
//		- close/cancel validation
//		- logs limit - clear only if necessary?
//		- toggle autoscroll

var VS = {
	
	initialize: function() {
		VS.logs = $('#logs');
		VS.logs.list = $('#logs ul');
		VS.stats = $('#stats');
		VS.stats.urls = $('#stats li.urls :last-child');
		VS.stats.observations = $('#stats li.observations :last-child');
		VS.stats.errors = $('#stats li.errors :last-child');
		VS.stats.warnings = $('#stats li.warnings :last-child');
		VS.form = $('#form form');
		VS.messages = $('#messages');
		VS.progress = $('#progress');
		VS.progress.bar = $('#progress progress');
		VS.progress.legend = $('#progress span');
		VS.currentActionId = "";
		
		VS.cometIframe = $('<iframe style="display:none"></iframe>');
		$('body').append(VS.cometIframe);
		
		VS.parseHash();
		VS.formValidateAction();
		
		$('a.clear').click(function () {
			VS.clearLogs();
			return false;
		});
		
		VS.WS = null;
	},
	
	formValidateAction: function() {
		var options = {
			success:	VS.subscribe,
			error:		VS.xhrError
		};
		//VS.form.ajaxForm(options); 
		VS.form.find('input[type=submit]').attr('value', 'Validate');
		VS.form.unbind('submit');
		VS.form.submit(function() {
			VS.clearLogs();
			VS.clearStats();
			VS.clearMessages();
			VS.clearAssertions();
			$(this).ajaxSubmit(options);
			return false;
		});
	},
	
	formStopAction: function() {
		VS.form.find('input[type=submit]').attr('value', 'Stop');
		VS.form.unbind('submit');
		VS.form.submit(function() {
			jsRoutes.controllers.Validator.stop(VS.currentActionId).ajax({
				success: function() {
					VS.formValidateAction();
				}
			});
			return false;
		});
	},
	
	subscribe: function(responseText, statusText, xhr) {
		VS.formStopAction();
		VS.log("<li class='status'>Starting crawl of " + $("input#url", VS.form).val() + " with a distance of " + $("input#distance", VS.form).val() + "</li>");
		var loc = xhr.getResponseHeader("Location");
		VS.currentActionId = xhr.getResponseHeader("X-VS-ActionId");
		if (window.location.pathname != "/") { // TODO should be dynamic?
			window.location = "/#!/observation/" + VS.currentActionId;
		} else {
			VS.setHash('!/observation/' + VS.currentActionId);
			//VS.cometIframe.attr('src', loc + "/stream");
			
			var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket;
			VS.WS = new WS("ws://localhost:9000/observation/" + VS.currentActionId + "/ws");
			
			VS.WS.onmessage = function(event) {
				VS.logComet(event.data);
			};
		}
	},
	
	xhrError: function(jqXHR, textStatus, errorThrown) {
		console.log(jqXHR);
		console.log(textStatus);
		console.log(errorThrown);
	},
	
	clearLogs: function() {
		VS.logs.list.empty();
	},
	
	clearStats: function() {
		VS.stats.urls.text("0/0");
		VS.stats.observations.text("0/0");
		VS.stats.errors.text("0");
		VS.stats.warnings.text("0");
	},
	
	/*logResponse: function(response) {
		if (response.state == "error") {
			console.log("Error state response:");
			console.log(response);
			return;
		}
		if (response.status == "404") {
			//console.log(response);
			VS.log($('<li>', {
				'class': 'error',
				 html  : 'Invalid observationID: ' + VS.parseHeader(response.headers)["X-VS-ObservationID"]
			}));
			VS.setHash("");
			return;
		}
		if (response.status != "200") {
			VS.log($('<li>', {
				'class': 'error',
				 html  : 'Error code: ' + response.status
			}));
			return;
		}
		if (response.responseBody != "") {
			try {
				var messages = response.responseBody.match(/\[[^\[]+\]/g);
				for (var i=0;i<messages.length;i++) {
					var msg = $.parseJSON(messages[i]);
					VS.logJson(msg);
				}
			} catch(e) {
				console.log(e.toString() + " - " + response.responseBody);
				VS.log(e.toString() + " - " + response.responseBody);
			}
		} else {
			console.log("Empty response body:");
			console.log(response);
		}
	},*/
	
	logComet: function(msg) {
		try {
			var messages = msg.match(/\[[^\[]+\]/g);
			for (var i=0;i<messages.length;i++) {
				var msg = $.parseJSON(messages[i]);
				VS.logJson(msg);
			}
		} catch(e) {
			console.log(e.toString() + " - " + msg);
			VS.log(e.toString() + " - " + msg);
		}
	},
	
	logJson: function(msg) {
		var type = msg[0];
		console.log(msg);
		if (type == "GET") {
			VS.incrementCrawled(1,msg[3]);
			var ext = "";
			if (parseInt(msg[3]) > 0)
				ext = " (+" + msg[3] + ")";
			VS.log($('<li>', {
				'class': 'fetch get c' + msg[1],
				 html  : '<span>GET</span> <span>(' + msg[1] + ')</span> <span>' + msg[2] + '</span> <span>' + ext + '</span>'
			}));
		} else if (type == "HEAD") {
			VS.incrementCrawled(1,0);
			VS.log($('<li>', {
				'class': 'fetch head c' + msg[1],
				 html  : '<span>HEAD</span> <span>(' + msg[1] + ')</span> <span>' + msg[2] + '</span>'
			}));
		} else if (type == "ERR") {
			//VS.log(response.responseBody);
			VS.log($('<li>', {
				'class': 'fetch error',
				 html  : '<span>ERROR</span> <span>(' + msg[1] + ')</span> <span>' + msg[2] + '</span>'
			}));
		} else if (type == "OBS") {
			VS.incrementObserved(1,0);
			var errors = parseInt(msg[3]);
			var warnings = parseInt(msg[4]);
			VS.incrementErrors(errors);
			VS.incrementWarnings(warnings);
			var str = " &nbsp;»&nbsp; ";
			if (errors == 0)
				if (warnings == 0)
					str += "<span class='valid'>valid</span>";
				else
					str += "<span class='warnings'>" + warnings + "</span> warning(s)";
			else {
				str += "<span class='errors'>" + errors + "</span> errors(s)";
				if (warnings > 0)
					str += " and <span class='warnings'>" + warnings + "</span> warning(s)";
			}
			VS.log($('<li>', {
				'class': 'obs',
				 html  : '<span>'+msg[2]+':</span> '+ msg[1] + str
			}));
			if (errors != 0 || warnings != 0)
			  VS.addAssertion(msg[1], "#", errors, warnings);
		} else if (type == "OBS_ERR") {
			VS.log(response.responseBody);
		} else if (type == "OBS_NO") {
			VS.log($('<li>', {
				'class': 'obs none',
				 html  : 'No observer available for '+ msg[1]
			}));
		} else if (type == "NB_EXP") {
			VS.incrementCrawled(0,msg[1]);
		} else if (type == "NB_OBS") {
			VS.log("<li class='status'>Starting observation phase</li>");
			VS.incrementObserved(0, msg[1]);
		} else if (type == "OBS_FINISHED") {
			VS.cometIframe.attr('src', ''); // TODO: Probably useless? Might be useful to clean the content for memory footprint 
			VS.log("<li class='status'>Validation finished</li>");
			VS.progress.css('display', 'none');
			VS.formValidateAction();
		} else if (type == "OBS_INITIAL") {
			VS.incrementCrawled(0,msg[1]+msg[2]);
			VS.incrementObserved(0,msg[3]+msg[4]);
		} else if (type == "STOPPED") {
			// TODO should wait for this message before changing form to validate action
			VS.cometIframe.attr('src', '');
			VS.progress.css('display', 'none');
			VS.log("<li class='status'>Validation stopped</li>");
		} else {
			console.log(msg);
			VS.log(msg);
		}
	},
	
	log: function(message) {
		console.log(message);
		if (typeof message === "string")
			message = $('<li>' + message + '</li>');
		VS.logs.list.append(message);
		VS.logs.scrollTop(VS.logs.list.height());
	},
	
	parseHeader: function(headers) {
		var arr = headers.split("\n");
		var result = {};
		for(var i=0;i<arr.length;i++) {
			var h = arr[i].split(':');
			result[h[0]] = $.trim(h[1]);
		}
		return result;
	},
	
	incrementCrawled: function(crawledInc, totalInc) {
		VS.incrementStats(crawledInc, totalInc, VS.stats.urls);
	},
	
	incrementObserved: function(obsInc, totalInc) {
		VS.incrementStats(obsInc, totalInc, VS.stats.observations);
	},
	
	incrementStats: function(left, right, node) {
		if (typeof node.text !== "function") {
			console.log("ERROR: object passed to incrementStats method is not a jQuery object");
			return;
		}
		var val = node.text().split("/");
		node.text(
			(parseInt(val[0]) + parseInt(left)) + "/" + (parseInt(val[1]) + parseInt(right))
		);
		
		VS.progress.css('display', 'inline-block');
		console.log(VS.progress);
		VS.progress.bar.attr('value', parseInt(val[0]) + parseInt(left));
		VS.progress.bar.attr('max', parseInt(val[1]) + parseInt(right));
	},
	
	incrementErrors: function(nb) {
		VS.stats.errors.text(
			parseInt(VS.stats.errors.text()) + nb
		);
	},
	
	incrementWarnings: function(nb) {
		VS.stats.warnings.text(
			parseInt(VS.stats.warnings.text()) + nb
		);
	},
	
	addAssertion: function(url, validatorLink, errors, warnings) {
		warnings = warnings == 0 ? "-" : warnings;
		errors = errors == 0 ? "-" : errors;
		$("#observations").css("display","block")
		$("#observations ul").append(
			$("<li><span>" + url + "</span><span>" + warnings + "</span><span>" + errors + "</span>")
		);
	},
	
	clearAssertions: function() {
		$("#observations").css("display","none");
		$("#observations li.not(:first-child)").remove();
	},
	
	addMessage: function(str) {
		VS.messages.append(
			$('<div class="error">'+ str +'</div>')
		);
	},
	
	clearMessages: function() {
		VS.messages.html('');
	},
	
	parseHash: function() {
		var hash = window.location.hash;
		if (hash.indexOf('#!/observation/') == 0)
			VS.cometIframe.attr('src', hash.substr(hash.search('/observation/[^/]+$')) + "/stream"); // XXX: could be hash.substr(2) for performance
	},
	
	setHash: function(hash){
		if (window.webkit419){
			W3C.FakeForm = W3C.FakeForm || new Element('form', {'method': 'get'}).injectInside(document.body);
			W3C.FakeForm.setProperty('action', '#' + hash).submit();
		} else {
			window.location.replace('#' + hash);
		}
	},
	
//    log: function (level, args) {
//        if (window.console) {
//            var logger = window.console[level];
//            if (typeof logger == 'function') {
//                logger.apply(window.console, args);
//            }
//        }
//    }
	
};

$(document).ready(function() {
	VS.initialize();
});