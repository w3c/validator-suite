require.config({
    paths: { 'libs/query': 'libs/zepto' },
    urlArgs: "bust=" +  (new Date()).getTime(),
    enforceDefine: true,
    shim: {
        'libs/underscore': { exports: '_' },
        'libs/query':      { exports: '$' },
        'libs/modernizr':  { exports: 'Modernizr' },
        'libs/backbone': {
            deps: ['libs/underscore', 'libs/query'],
            exports: 'Backbone'
        },
        'libs/foundation': {
            deps: ['libs/modernizr', 'libs/query'],
            exports: 'Foundation'
        },
        'libs/foundation/reveal': ['libs/foundation'],
        'libs/foundation/orbit':  ['libs/foundation']
    }
});