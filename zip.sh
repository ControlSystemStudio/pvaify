#!/bin/sh

# Collect distribution in this folder
D=pvaify
# Name of ZIP
Z=pvaify.zip

rm -rf $D
mkdir $D
cp README.MD pvaify.sh $D
cp target/pvaify-*.jar $D
mkdir $D/lib
cp -r target/lib/*.jar $D/lib
cp -r demo $D

rm -f $Z
zip -r $Z $D

echo "-------------------------------------------"

unzip -v $Z
