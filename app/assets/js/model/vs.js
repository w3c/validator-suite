define([
    "model/model",
    "model/collection",
    "model/job",
    "model/jobs",
    "model/assertion",
    "model/assertions",
    "model/resource",
    "model/resources",
    "model/assertor",
    "model/assertors"],

    function () {
        'use strict';
        return {
            Model:      require("model/model"),
            Collection: require("model/collection"),
            Job:        require("model/job"),
            Jobs:       require("model/jobs"),
            Assertion:  require("model/assertion"),
            Assertions: require("model/assertions"),
            Resource:   require("model/resource"),
            Resources:  require("model/resources"),
            Assertor:   require("model/assertor"),
            Assertors:  require("model/assertors")
        };
    });
