#!/usr/bin/env bash

export LEIN_SNAPSHOTS_IN_RELEASE=true

echo "!! Updating version string in src/main/resources ..."
source bin/_utils.sh
V=`get_version`
VF=src/main/resources/george-version.txt
echo -n $V > $VF
cat $VF

echo "!! Building ..."
# Ensure any old compiled classed are removed first.
lein clean
lein uberjar