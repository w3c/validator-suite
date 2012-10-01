// https://github.com/jrburke/r.js/blob/master/build/example.build.js
({
    appDir: "./scripts",
    baseUrl: "./app",
    dir: "../../public/scripts", // /!\ Directory purged on each build
    //optimize: "none",
    //optimize: "closure",
    optimize: "closure.keeplines",
    paths: {
        "libs": "../libs",
        "libs/jquery": "../libs/jquery-1.7.1"
    },
    modules: [
        {
            name: "libs/backbone"
        },
        {
            name: "dashboard",
            exclude: ["libs/backbone"]
        }
    ]
})