$(function () {
/*
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
*/

    //Backbone.emulateJSON = true;

    var VS = window.VS = {

        dashboard: new W3.JobsView({
            el: document.getElementById("jobs"),
            url: "/suite/jobs",
            jobTemplate: _.template(document.getElementById("job-template").text)
        }),

        jobupdate: function (job) {
            this.socket.trigger("jobupdate", job);
        },

        socket: (function () {
            var socket = new W3.Socket("/suite/jobs");
            //socket.on("open", function () {console.log("open")});
            //socket.on("close", function () {console.log("close")});
            socket.on("jobupdate", function (data) {
                var job = VS.dashboard.collection.get(data.id);
                if (!_.isUndefined(job)) {
                    job.set(data);
                } else {
                    console.log("unknown job with id: " + data.id);
                    console.log(data);
                }
            });
            //socket.on("error", function () {console.log("error")});
            //socket.onmessage = _.bind(onmessage, this);
            return socket;
        })()

    };

    $('.pagination form button').css('display', 'none');
    var pageSelect = $('.pagination select');
    pageSelect.change(function () {
        pageSelect.parents('form').submit();
    });

    VS.dashboard.fetch();

/*    $.ajax({
        url: "/suite/jobs",
        success: function (json) {
            VS.dashboard.collection.reset(
                _.map(json, function (job) { return new W3.Job(job); })
            );
        }
    });*/

});