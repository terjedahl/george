#!/usr/bin/env bash

lib_dir=javafx-libs/Linux

#jdk_dir=target/jdk
#tar_file=target/jdk/linux-jdk.tar.gz
#mkdir -p $jdk_dir
#curl -o $tar_file https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_linux-x64_bin.tar.gz
#tar -xzf $tar_file --directory /usr/lib/jvm
#rm -r $jdk_dir
#echo Append the following lines to your .bashrc :
#echo '  export JAVA_HOME="/usr/lib/jvm/jdk-11.0.2"'
#echo '  export JDK_HOME="/jdk-10.0.1"'
#echo '  export PATH="$JAVA_HOME/bin:$PATH"'

echo Ensuring dir $lib_dir ...
mkdir -p $lib_dir
cd $lib_dir

echo Downloading JavaFX SDK ...
curl --output sdk.zip -L https://gluonhq.com/download/javafx-11-0-2-sdk-linux
echo Unzipping ...
unzip -qu sdk.zip
rm sdk.zip

echo Downloading JavaFX jmods ...
curl --output jmods.zip -L https://gluonhq.com/download/javafx-11-0-2-jmods-linux
echo Unzipping ...
unzip -qu jmods.zip
rm jmods.zip

cd ../..
echo $lib_dir:
ls -l $lib_dir
