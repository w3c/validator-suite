// https://github.com/jrburke/r.js/blob/master/build/example.build.js
({
    baseUrl: "./",
    preserveLicenseComments: false,
    optimize: "uglify",
    wrap: {
        startFile: ["../js/licences.txt"],
        endFile: []
    },
    paths: { },
    shim: {
        'libs/jquery':     { exports: '$' },
        'libs/underscore': { exports: '_' },
        'libs/modernizr':  { exports: 'Modernizr' },
        'libs/backbone': {
            deps: ['libs/underscore', 'libs/jquery'],
            exports: 'Backbone'
        },
        'libs/foundation': {
            deps: ["libs/modernizr", "libs/jquery"],
            exports: "Foundation"
        },
        'libs/foundation.reveal': ['libs/foundation'],
        'libs/foundation.orbit': ['libs/foundation'],
        'libs/foundation.dropdown': ['libs/foundation']
    },
    modules: [
        {
            name: 'libs/jquery'
        },
        {
            name: "main"
        },
        {
            name: "front"
        },
        {
            name: "console"
        }
    ]
})