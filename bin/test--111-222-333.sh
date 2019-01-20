#!/usr/bin/env bash

echo
echo "Clean and build jre"
echo "Build and deploy :ts 333"
echo "Build and install :ts 222"
echo "Build and launch :ts 111"
echo
echo "Expect: Start :ts 333"
echo
sleep 2.0

lein clean; lein build jre
lein build jar :ts 333; lein build site; lein aws deploy
lein build jar :ts 222; lein installed install
lein build jar :ts 111; debug=1 lein jre :jar $@