Validator Suite
---------------

### Play 2.1-SNAPSHOT

For the time being, use [this particular commit](https://github.com/playframework/Play20/commit/82fb6f472698cdba3178ebb2f67a2062aec9125b)

```
git clone git://github.com/playframework/Play20.git
cd Play20/framework
git checkout 82fb6f472698cdba3178ebb2f67a2062aec9125b
./build publish-local
```

### start geeking

You need Java 7. First build will take time as it fetches all the dependencies.

```
git clone git@github.com:w3c/validator-suite.git
cd validator-suite
<path-to-Play20>/play
```