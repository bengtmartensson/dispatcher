#!/bin/sh

#JAVA_HOME=/opt/jre1.7.0_55
#JAVA=${JAVA_HOME}/bin/java
JAVA=java
DISPATCHER_HOME=/usr/local/dispatcher

cd ${DISPATCHER_HOME}
echo $$ > /var/run/dispatcher.pid

exec ${JAVA} -Djava.library.path=/usr/local/lib -jar ${DISPATCHER_HOME}/dist/Dispatcher.jar --config ${DISPATCHER_HOME}/listener.xml --device /dev/ttyUSB0 --increment --baud 115200 --logfile /var/log/dispatcher.log
