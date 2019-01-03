#!/usr/bin/env bash

export LEIN_SNAPSHOTS_IN_RELEASE=true
source bin/_utils.sh

echo "!! Updating version string in src/rcs ..."
V=`get_version`
VF=src/rsc/george-version.txt
echo -n $V > $VF
cat $VF
echo

echo "!! Building ..."
# Ensure any old compiled classed are removed first.
lein clean
lein uberjar