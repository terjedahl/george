#!/usr/bin/env bash

# '$0' is possibly a symlink-path, so we need the "real"(dereferenced) path
app_dir=$(dirname $(realpath "$0"))

source $app_dir/bin/utils.sh


_start_logging() {
    # TODO: Find a better place for logs?
    logdir="/tmp/log/{{ app }}/$USER"
    [ ! -d $logdir ] && mkdir -p $logdir
    export logfile="$logdir/{{ app }}_$(now_basic).log"
    exec &> >(tee -i $logfile)
    echo
    echo "START: [$(now_extended)]"
    echo
}


launch() {
    _start_logging

    local java_cmd="$app_dir/jre/bin/java"
    local splash="$app_dir/rsc/{{ splash-image }}"
    local jar="$(ls "$app_dir"/jar/{{ jar-name }})"

    export APPLICATION_NAME="{{ app }}"  # Picked up by no.andante.george.Agent
    "$java_cmd" "-splash:$splash" "-Dprism.dirtyopts=false" "-javaagent:$jar"  -jar "$jar" "$@"
}


main() {
    case $1 in
        "installer") shift; VIA_GEORGE=1 "$app_dir/installer.sh" $@ ;;
        *)           launch $@ ;;
    esac
}


main $@