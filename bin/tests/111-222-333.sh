#!/usr/bin/env bash

echo
echo "clean - build jre"
echo "build jar - build site - start site  :ts 333"
echo "build jar - install                  :ts 222"
echo "build jar - launch jar on jre        :ts 111"
echo "stop site"
echo
echo "Expect: Start :ts 333"
echo

sleep 2.0

app=`lein pprint "[:build :properties :app]" | sed -e 's/^"//' -e 's/"$//'`
platform=`lein exec -ep "(require '[common.george.util.platform :as p]) (println (p/platform))"`
uri="http://localhost:9999/apps/${app}/platforms/${platform}/jar/"

lein clean; lein build jre
lein build jar :ts 333 :uri $uri; lein build site; lein site start &
lein build jar :ts 222 :uri $uri; lein installed install
lein build jar :ts 111 :uri $uri; debug=1 lein jre :jar $@
debug=1 lein site stop