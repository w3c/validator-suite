Validator Suite
---------------

### Play 2.0 RC1

* [latest commit](https://github.com/playframework/Play20/commit/c05152254a4ff06728ee785940e2584cc235ed14)

    git clone git://github.com/playframework/Play20.git
    cd Play20/framework
    git checkout c05152254a4ff06728ee785940e2584cc235ed14
    ./build publish-local
    cd ..
    cp -r repository/local/play ~/.ivy2/local

Then make `Play20/framework` part of your `PATH`

    export PATH=$PATH:/path/to/play20

### start geeking

    hg clone https://dvcs.w3.org/hg/validator-suite
    cd validator-suite
    ./sbt 'eclipse same-target'

The only requirement is Java 6. Then sbt will take care of the rest. This can take several minutes the very first time.

### Eclipse

Be sure you ran '''eclipse same-target''' from sbt. The visit the following links:

* [http://www.scala-ide.org/](http://www.scala-ide.org/)
* [https://www.assembla.com/wiki/show/scala-ide/Setup](https://www.assembla.com/wiki/show/scala-ide/Setup)
* [https://www.assembla.com/spaces/scala-ide/wiki/Getting_Started](https://www.assembla.com/spaces/scala-ide/wiki/Getting_Started)

