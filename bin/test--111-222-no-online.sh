#!/usr/bin/env bash

echo
echo "Clean and build jre"
echo "Build and install :ts 222"
echo "Build and launch :ts 111 :no-online"
echo
echo "Expect: Start :ts 222"
echo
sleep 2.0

lein clean; lein build jre
lein build jar :ts 222; lein installed install
lein build jar :ts 111; debug=1 lein jre :jar :no-online-check $@