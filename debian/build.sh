#!/bin/bash -ex

cd `dirname $0`

# copy the jars and the configuration file
rm -Rf lib
mkdir -p lib
cp ../target/staged/*.jar lib
cp ../conf/application.conf .

# update vs.install
cat > debian/vs.install <<EOF
managed.conf etc/vs/conf/
application.conf etc/vs/conf/
wgrep usr/bin/
EOF
for i in lib/*.jar; do
    f=$(basename $i)
    echo "lib/$f usr/share/vs" >> debian/vs.install
done

debuild -us -uc -b

rm -Rf lib application.conf
