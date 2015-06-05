#!/bin/sh

DESTDIR=/usr/local/dispatcher
BINDIR=/usr/local/bin

mkdir -p ${DESTDIR}
cp target/Dispatcher-*-jar-with-dependencies.jar ${DESTDIR}
cp src/main/config/dispatcher.sh ${DESTDIR}
ln -sf ${DESTDIR}/dispatcher.sh ${BINDIR}/dispatcher

if [ ! -e ${DESTDIR}/listener.xml ] ; then
    cp src/main/config/listener.xml ${DESTDIR}
else
    echo "NOT overwriting existing ${DESTDIR}/listener.xml"
fi
