require.config({
    paths: {
        'libs/query': ('__proto__' in {}) ? 'libs/zepto' : 'libs/jquery'
    },
    //urlArgs: "bust=" +  (new Date()).getTime(),
    //enforceDefine: true,
    shim: {
        'libs/underscore': { exports: '_' },
        'libs/query':      { exports: '$' },
        'libs/modernizr':  { exports: 'Modernizr' },
        'libs/backbone': {
            deps: ['libs/underscore', 'libs/query'],
            exports: 'Backbone'
        },
        'libs/foundation/foundation': {
            deps: ["libs/modernizr", "libs/query"],
            exports: "Foundation"
        },
        'libs/foundation/foundation.reveal': ['libs/foundation/foundation'],
        'libs/foundation/foundation.orbit': ['libs/foundation/foundation']
    }
});