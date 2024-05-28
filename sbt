#!/usr/bin/env bash

# Debug option
DEBUG_PARAMS=""
COLOUR_PARAMS=""

CONF_PARAMS="-Dconfig.file=$HOME/.gu/security-hq.local.conf"
for arg in "$@"
do
    if [ "$arg" == "--debug" ]; then
      echo "Setting java process as debuggable"
      DEBUG_PARAMS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1058"
      shift
    fi
    if [ "$arg" == "--no-conf" ]; then
      echo "Not using local conf file"
      CONF_PARAMS=""
      shift
    fi
done

java $DEBUG_PARAMS \
    -Xms1024M -Xmx4096M \
    -Xss1M \
    $CONF_PARAMS \
    $COLOUR_PARAMS \
    -jar bin/sbt-launch.jar "$@"
