#!/bin/bash

# Configure PVA server side
# See PVASettings.java for details

# UDP port that listens to searches
# Client needs to set EPICS_PVA_BROADCAST_PORT to match
export EPICS_PVAS_BROADCAST_PORT=5076

# TCP port for searches and data
# (will use random one when not available)
export EPICS_PVA_SERVER_PORT=5075

# TLS port for searches and data
# (will use random one when not available)
export EPICS_PVAS_TLS_PORT=5076

# Enable Secure PVA by providing a server certificate.
# Clients will need a suitable client certificate.
# Leave empty to disable support for security.
export EPICS_PVAS_TLS_KEYCHAIN=~/.config/pva/1.3/server.p12


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

