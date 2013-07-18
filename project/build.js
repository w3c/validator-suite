// https://github.com/jrburke/r.js/blob/master/build/example.build.js
({
    baseUrl: "./",
    //mainConfigFile: '../js/config.js',
    preserveLicenseComments: false,
    wrap: {
        startFile: ["../js/licenses.txt"],
        endFile: []
    },
    paths: {
        'libs/query': "empty:"
    },
    shim: {
        'libs/underscore': { exports: '_' },
        'libs/modernizr':  { exports: 'Modernizr' },
        'libs/backbone': {
            deps: ['libs/underscore', 'libs/query'],
            exports: 'Backbone'
        },
        'libs/foundation': {
            deps: ["libs/modernizr", "libs/query"],
            exports: "Foundation"
        },
        'libs/foundation.reveal': ['libs/foundation'],
        'libs/foundation.orbit': ['libs/foundation']
    },
    modules: [
        {
            name: "libs/require",
            include: ["libs/zepto", 'config']
        },
        {   name: "libs/backbone"   },
        {
            name: "model/vs",
            exclude: ["libs/backbone"]
        },
        {
            name: "main",
            exclude: ["libs/backbone", "model/vs"]
        },
        {
            name: "front"
        }
    ]
})