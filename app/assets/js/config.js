
require.config({
    //enforceDefine: true,
    //urlArgs: "bust=" +  (new Date()).getTime(),
    shim: {
        'libs/backbone': ['libs/query'],
        'front': ['libs/query']
    }
});

//define('libs/query', [('__proto__' in {}) ? 'libs/zepto' : 'libs/jquery'], function () { return $; });
define('libs/query', ['libs/jquery'], function () { return $; });
