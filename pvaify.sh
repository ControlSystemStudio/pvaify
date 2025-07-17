#!/bin/bash

CP="target/classes"
CP+=":../phoebus/core/framework/target/classes"
CP+=":../phoebus/core/pv/target/classes"
CP+=":../phoebus/core/pv-ca/target/classes"
CP+=":../phoebus/core/pva/target/classes"
CP+=":../phoebus/dependencies/phoebus-target/target/lib/jca-2.4.9.jar"
CP+=":../phoebus/dependencies/phoebus-target/target/lib/vtype-1.0.7.jar"
CP+=":../phoebus/dependencies/phoebus-target/target/lib/epics-util-1.0.7.jar"
CP+=":../phoebus/dependencies/phoebus-target/target/lib/reactive-streams-1.0.3.jar"
CP+=":../phoebus/dependencies/phoebus-target/target/lib/rxjava-3.0.9.jar"


java -cp $CP org.phoebus.pvaify.Main

