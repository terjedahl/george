#!/usr/bin/env bash

logdir="$HOME/Library/Logs/{{ app }}"
[ ! -d $logdir ] && mkdir -p $logdir

logfile="$logdir/{{ app }}_`date '+%Y%m%dT%H%M%S'`.log"
exec &> >(tee -i $logfile)

echo "START: [$(date '+%Y-%m-%d %H:%M:%S')]"
echo

MacOS=$(cd "$(dirname "$0")"; pwd)

Contents=$(cd "$MacOS/.."; pwd)
echo Contents: $Contents

java_cmd=$Contents/jre/bin/java

icon=$Contents/Resources/George.icns

jar=$(ls "$Contents"/jar/{{ jar-name }})

"$java_cmd" "-Xdock:icon=$icon" -jar  "$jar" $@
