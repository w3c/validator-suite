define(["w3", "model/jobs", "model/job"], function (W3, Jobs, Job) {

    var Debug = {};

    window.Debug = Debug;

    Debug.addJobsTo = function(collection) {

        var i = 0;
        var jobs = [];
        for (i; i < 10; i++) {
            jobs.push(new Job.Model({
                name: "job" + i,
                entrypoint: "http://w3.org/" + (new Date()).getMilliseconds(),
                assertor: ["validator_html"],
                maxResources: i % 2 + 1,
                errors: i % 3,
                warnings: i % 4,
                resources: i % 5
            }));
        }

        collection.add(jobs);

        _.each(jobs, function (job) { job.save(); job.fetch(); });

    };

    return Debug;

});