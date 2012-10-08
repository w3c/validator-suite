require(["w3", "model/job", "model/jobs"], function (W3, Job, Jobs) {

    // for debugging
    window.W3 = W3;
    window.Job = Job;
    window.Jobs = Jobs;

    if(!('position' in document.createElement('progress'))) {
        require(["libs/progress-polyfill"]);
    }

    $(function () {

        var root, template, jobs, socket, VS;

        root = window;

        //header = $('body > header'),
        //footer = $('body > footer');

        $("#actions .search button").hide();
        $("#actions .search input").addClass("cleared");
        $("#actions .clear").remove();
        $('nav.pagination :not(p.legend)').hide();
        $('body > footer').addClass('jsFixed');
        // TODO height + padding-top + padding-bottom
        //$('#main').css("padding-bottom", footer.height() + "px");
        $('#main').css("padding-bottom", "50px");


        try {
            template = _.template(document.getElementById("job-template").text);
        } catch(ex) {
            template = _.template("");
        }

        jobs = root.jobs = new Jobs.View({
            el: document.getElementById("jobs"),
            jobTemplate: template,
            load: true
        });

        // for comet, deprecated
        VS = root.VS = {};
        VS.jobupdate = function (job) {
            socket.trigger("jobupdate", job);
        };

    });

});