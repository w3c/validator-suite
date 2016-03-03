W3C Validator Suite
---------------

## Status

Note: W3C is not running Validator Suite nor intending to continue development at this time (see [W3C communication](https://www.w3.org/2015/08/validator-suite-service-discontinuation) from August 2015).  Should there be interest from active developers in the community in furthering and maintaining it please let us know.

Since there is no resources from W3C nor the community supporting this project at present, we encourage people to fork.

## Project dependencies

### Java

You need the Java 7 JDK.

On Debian based systems use [OpenJDK](http://openjdk.java.net/): `apt-get install openjdk-7-jdk`  
On Windows hosts install [Oracle's Java](http://www.oracle.com/technetwork/java/javase/downloads/index.html)

### Play Framework

W3C Validator Suite is based on the [Play Framework](http://www.playframework.com/).  
The Play framework embeds its own scala version.

```bash
wget http://downloads.typesafe.com/play/2.2.1/play-2.2.1.zip
unzip play-2.2.1.zip
ln -s play-2.2.1 play
```

### MongoDB

W3 Validator Suite uses [MongoDB](http://www.mongodb.org/) as its data storage.  
The code has been tested with version 2.4+.

On Debian based systems (_mongodb 2.4.8 is available in [Debian Wheezy Backports](http://packages.debian.org/wheezy-backports/mongodb)_): `apt-get install mongodb`  
On Windows hosts install the latest ["Production Release" of MongoDB](http://www.mongodb.org/downloads).

## Start geeking

Now start `play` and start playing!  
_First build will take time as it fetches all the dependencies._

```bash
git clone git@github.com:w3c/validator-suite.git
cd validator-suite
<path-to-play>/play
```

Some sample commands:
* `help` - Displays Play help message.
* `tasks` - Lists the tasks defined for the current project.
* `clean` - _W3C Validator Suite:_ Deletes files produced by the build, such as generated sources, compiled classes, and task caches.
* `doc` - _W3C Validator Suite:_ Generates API documentation.
* `run` - _W3C Validator Suite:_ Runs the application in dev mode.
* `run-main org.w3.vs.Main default` - _W3C Validator Suite:_ Resets the whole database and adds the root users.
* `test` - _W3C Validator Suite:_ Executes all tests.
