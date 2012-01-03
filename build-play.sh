git clone git://github.com/playframework/Play20.git
# also: git pull git://github.com/playframework/Play20.git
cd Play20/framework
# git checkout f0021a73a360cadbbd4da38ceb2d3d6b2e9e45d8
./build publish-local
cd ..
# rm -Rf ~/.ivy2/local
cp -r repository/local/play ~/.ivy2/local
