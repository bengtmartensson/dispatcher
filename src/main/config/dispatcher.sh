#!/bin/sh
# Example wrapper to start Dispatcher
# For Unix-like systems

JAVA=java
DISPATCHER_HOME=/usr/local/share/dispatcher
RXTX_LOCATION=/usr/lib/jni
VERSION=0.0.2
# Requires suitable udev rules
# Use the most specific name possible, preferrably with unique serial
#DEVICE=/dev/arduino
#DEVICE=/dev/arduino_nano_prolific
DEVICE=/dev/ttyACM0
BAUD=115200

LOGFILE=/var/log/dispatcher.log
if touch ${LOGFILE} >/dev/null 2>&1 ; then
    true ;
else
    LOGFILE=${HOME}/dispatcher.log ;
fi

PIDFILE=/var/run/dispatcher.pid
if touch ${PIDFILE} >/dev/null 2>&1 ; then
    echo $$ > ${PIDFILE}
fi

exec ${JAVA} -Djava.library.path=${RXTX_LOCATION} \
     -jar ${DISPATCHER_HOME}/Dispatcher-${VERSION}-jar-with-dependencies.jar \
     --config ${DISPATCHER_HOME}/listener.xml \
     --device ${DEVICE} --baud ${BAUD} \
     --logfile ${LOGFILE} --loglevel INFO
