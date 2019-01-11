#!/bin/bash

LOGGED_IN_USER_ID=`id -u "${USER}"`

if [ "${COMMAND_LINE_INSTALL}" = "" ]
then
    /bin/launchctl asuser "${LOGGED_IN_USER_ID}" /usr/bin/open -b {{ identifier }} # /Applications/{{ app }}.app # PATH_OR_BUNDLE_ID
fibrune3

exit 0