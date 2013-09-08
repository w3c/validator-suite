// https://github.com/jrburke/r.js/blob/master/build/example.build.js
({
    baseUrl: "./",
    //mainConfigFile: '../js/config.js',
    preserveLicenseComments: false,
    optimize: "uglify",
    wrap: {
        startFile: ["../js/licences.txt"],
        endFile: []
    },
    paths: {
        //'libs/query': "libs/jquery" //"empty:"
    },
    shim: {
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
        'libs/foundation.orbit': ['libs/foundation']
    },
    modules: [
        {
            name: 'libs/jquery'
        },
        {
            name: "libs/require"
            //include: ['config' ]
        },
        {   name: "libs/backbone"   },
        {
            name: "model/vs",
            exclude: ["libs/backbone"]
        },
        {
            name: "main",
            exclude: ["libs/backbone", "model/vs"],
        },
        {
            name: "front",
        }
    ]
})