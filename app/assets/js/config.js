require.config({
    paths: {
        'libs/query': ('__proto__' in {}) ? 'libs/zepto' : 'libs/jquery'
    },
    shim: {
        'libs/query': { exports: '$' },
        'libs/backbone': {
            deps: ['libs/query'],
            exports: 'Backbone'
        },
        'libs/foundation': {
            deps: ['libs/query'],
            exports: 'Foundation'
        }
    }
});