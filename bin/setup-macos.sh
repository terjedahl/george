#!/usr/bin/env bash

lib_dir=javafx-libs/MacOS

echo Ensuring dir $lib_dir ...
mkdir -p $lib_dir
cd $lib_dir

echo Downloading JavaFX SDK ...
curl --output sdk.zip -L https://gluonhq.com/download/javafx-11-0-2-sdk-mac
echo Unzipping ...
unzip -qu sdk.zip
rm sdk.zip

echo Downloading JavaFX jmods ...
curl --output jmods.zip -L https://gluonhq.com/download/javafx-11-0-2-jmods-mac
echo Unzipping ...
unzip -qu jmods.zip
rm jmods.zip

cd ../..
echo $lib_dir:
ls -l $lib_dir
