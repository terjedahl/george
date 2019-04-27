#!/usr/bin/env bash

set -eo pipefail

JDK_NR="11.0.2"
JFX_NR="12-0-1"

JVM_DIR=/usr/lib/jvm
JFX_LIB_DIR=javafx-libs/Linux

_java() {
    local jdk_dir=target/jdk
    local tar_file=target/jdk/linux-jdk.tar.gz
    mkdir -p $jdk_dir
    curl -o $tar_file https://download.java.net/java/GA/jdk11/9/GPL/openjdk-${JDK_NR}_linux-x64_bin.tar.gz
    sudo tar -xzf $tar_file --directory $JVM_DIR
    rm -r $jdk_dir
    echo Append the following lines to your .bashrc :
    echo '  export JAVA_HOME="${JVM_DIR}/jdk-${JDK_NR}"'
    echo '  export JDK_HOME="/jdk-${JDK_NR}"'
    echo '  export PATH="$JAVA_HOME/bin:$PATH"'
}

_javafx() {
    echo Ensuring dir ${JFX_LIB_DIR} ...
    mkdir -p ${JFX_LIB_DIR}
    cd ${JFX_LIB_DIR}

    echo Downloading JavaFX SDK ...
    curl --output sdk.zip -L https://gluonhq.com/download/javafx-${JFX_NR}-sdk-linux
    echo Unzipping ...
    unzip -qu sdk.zip
    rm sdk.zip

    echo Downloading JavaFX jmods ...
    curl --output jmods.zip -L https://gluonhq.com/download/javafx-${JFX_NR}-jmods-linux
    echo Unzipping ...
    unzip -qu jmods.zip
    rm jmods.zip

    cd ../..
    echo "${JFX_LIB_DIR}:"
    ls -l $JFX_LIB_DIR
}


_usage() {
echo "
  Usage: $1 <task>

  Where <task> is one of:
    java     Install OpenJDK '${JDK_NR}' in directory '${JVM_DIR}' (requires sudo privileges)
    javafx   Install JavaFX '${JFX_NR}' JDK and jmods in '${JFX_LIB_DIR}'
"
}

case $1 in
    java)   _java ;;
    javafx) _javafx ;;
    *)      _usage $0;;
esac