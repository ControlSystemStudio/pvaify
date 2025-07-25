#!/bin/bash

# Is there a JAR?
JAR=`echo target/pvaify-*.jar`

if [ -r $JAR ]
then
    # Use maven-built jar
    java -jar $JAR "$@" 
else
    # Use IDE-provided classes and dependencies
    echo "Using development version"
    CP="target/classes"
    CP+=":../phoebus/core/framework/target/classes"
    CP+=":../phoebus/core/pv/target/classes"
    CP+=":../phoebus/core/pv-ca/target/classes"
    CP+=":../phoebus/core/pva/target/classes"
    LIB=`echo ../phoebus/dependencies/phoebus-target/target/lib/jca-[0-9.]*.jar`
    CP+=":$LIB"
    LIB=`echo ../phoebus/dependencies/phoebus-target/target/lib/vtype-[0-9.]*.jar`
    CP+=":$LIB"
    LIB=`echo ../phoebus/dependencies/phoebus-target/target/lib/epics-util-[0-9.]*.jar`
    CP+=":$LIB"
    LIB=`echo ../phoebus/dependencies/phoebus-target/target/lib/reactive-streams-[0-9.]*.jar`
    CP+=":$LIB"
    LIB=`echo ../phoebus/dependencies/phoebus-target/target/lib/rxjava-[0-9.]*.jar`
    CP+=":$LIB"

    echo $CP

    java -cp $CP org.phoebus.pvaify.Main "$@"
fi

