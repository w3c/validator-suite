$(function () {

    var htmlToJob = function (elem) {

        var $elem = $(elem);

        var _value = function (attribute) {
            var tag = $elem.find('[' + attribute + ']');
            var attr = tag.attr(attribute);
            if (attr !== "") {
                return attr;
            } else {
                return tag.text();
            }
        };

        return new W3.Job({
            id: $elem.attr("data-id"),
            name: _value('data-job-name'),
            entrypoint: _value('data-job-entrypoint'),
            status: _value('data-job-status'),
            completedOn: {
                timestamp: _value('data-job-completed'),
                legend1: _value('data-job-completed-legend1'),
                legend2: _value('data-job-completed-legend2')},
            warnings: parseInt(_value('data-job-warnings')),
            errors: parseInt(_value('data-job-errors')),
            resources: parseInt(_value('data-job-resources')),
            maxResources: parseInt(_value('data-job-maxResources')),
            health: {
                value: parseInt(_value('data-job-health')),
                legend: _value('data-job-health-legend')}
        });
    };

    var dashboard = new W3.JobsView({
        el: document.getElementById("jobs"),
        jobTemplate: _.template(document.getElementById("job-template").text)
    });

    dashboard.jobs.reset(_.map($("#jobs .job").get(), htmlToJob));

    var _callback = function (event) {
        try {
            var json = $.parseJSON(event.data);

            //console.log(event.data);

            if (json[0] != "Job") {
                console.log("Not a job update message: " + json[0]);
                return;
            }

            var jobUpdate = {id: json[1]};

            var addParam = function (name, value) {
                var b = {};
                b[name] = value;
                if (value !== null) _.extend(jobUpdate, b);
            }

            addParam("name", json[2]);
            addParam("entrypoint", json[3]);
            addParam("status", json[4]);

            if (json[5] !== null) {
                jobUpdate["completedOn"] = {
                    timestamp: json[5],
                    legend1: json[6],
                    legend2: json[7]
                };
            }

            addParam("warnings", json[8]);
            addParam("errors", json[9]);
            addParam("resources", json[10]);
            addParam("maxResources", json[11]);

            if (json[12] != null) {
                jobUpdate["health"] = {
                    value: json[12],
                    legend: json[12] + "%"
                };
            }

            var job = dashboard.jobs.get(jobUpdate.id);
            if (!_.isUndefined(job)) {
                job.set(jobUpdate);
            } else {
                console.log("unknown job with id: " + jobUpdate.id);
                console.log(jobUpdate);
            }

        } catch(ex) {
            console.log(ex);
        }

    };

    W3.Socket.open({
        url: "/suite/jobs/ws",
        onmessage: _.bind(_callback, this)
    });

});