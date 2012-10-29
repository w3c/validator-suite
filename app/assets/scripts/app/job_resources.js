require(["w3", "model/job", "collection/jobs", "model/resource", "collection/resources"], function (W3, Job, Jobs, Resource, Resources) {

    // for debugging
    window.W3 = W3;
    window.Job = Job;
    window.Jobs = Jobs;

    if(!('position' in document.createElement('progress'))) {
        require(["libs/progress-polyfill"]);
    }

    $(function () {

        var root, template, resourceTemplate, jobs, resources, socket, VS;

        root = window;

        $('nav.pagination :not(p.legend)').hide();
        $('body > footer').addClass('jsFixed');
        // TODO height + padding-top + padding-bottom
        //$('#main').css("padding-bottom", footer.height() + "px");
        $('#main').css("padding-bottom", "50px");

        //try {
            template = _.template(document.getElementById("job-template").text);
            resourceTemplate = _.template(document.getElementById("resource-template").text);
        //} catch(ex) {
        //    console.log('template not found');
        //    template = _.template("");
        //    resourceTemplate = _.template("");
        //}

        jobs = root.jobs = new Jobs.View({
            el: document.getElementById("jobs"),
            jobTemplate: template
        });

        resources = root.resources = new Resources.View({
            el: document.getElementById("resources"),
            template: resourceTemplate,
            load: true,
            searchInput: $('#jobs input.search')
        });

        // for comet, deprecated
        VS = root.VS = {};
        VS.jobupdate = function (job) {
            socket.trigger("jobupdate", job);
        };

    });

});