#!/bin/sh
# Example wrapper to start Dispatcher
# For Unix-like systems

JAVA=java
DISPATCHER_HOME=/usr/local/share/dispatcher
VERSION=0.1.0-SNAPSHOT

#DEVICE=/dev/arduino
#DEVICE=/dev/arduino_nano_prolific
#DEVICE=/dev/ttyACM0
#DEVICE=/dev/serial/by-id/usb-Arduino_LLC_Arduino_Micro-if00
DEVICE=/dev/serial/by-id/usb-Prolific_Technology_Inc._USB-Serial_Controller-if00-port0
BAUD=115200

LOGFILE=/var/log/dispatcher.log
if touch ${LOGFILE} >/dev/null 2>&1 ; then
    true ;
else
    LOGFILE=${HOME}/dispatcher.log ;
fi

#PIDFILE=/var/run/dispatcher.pid
#if touch ${PIDFILE} >/dev/null 2>&1 ; then
#    echo $$ > ${PIDFILE}
#fi

exec ${JAVA} \
     -jar "${DISPATCHER_HOME}/Dispatcher-${VERSION}-jar-with-dependencies.jar" \
     --apphome "${DISPATCHER_HOME}" \
     --config "${DISPATCHER_HOME}/listener.xml" \
     --device "${DEVICE}" --baud ${BAUD} \
     --logfile "${LOGFILE}" --loglevel INFO
