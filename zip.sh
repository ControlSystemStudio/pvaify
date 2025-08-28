#!/bin/sh

Z=pvaify.zip

rm -f $Z
zip $Z README.MD pvaify.sh
(cd target; zip ../$Z pvaify-*.jar lib/*.jar)
zip -r $Z demo


echo "-------------------------------------------"

unzip -v $Z
