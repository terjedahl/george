#!/usr/bin/env bash


# ISO UTC (Z)
now_extended() {
    echo $( date --utc '+%Y-%m-%dT%H:%M:%SZ' )
}
# ISO UTC (Z)
now_basic() {
    echo $( date --utc '+%Y%m%dT%H%M%SZ' )
}


# $1 - app-dir:     Dir where all resources are installed/located
# $2 - desktop-dir: Dir to which .desktop file should be written
# $3 - as_sudo:     Optional argument; if $3 == "sudo"; then as sudo
write_desktop_file() {
  local app_dir=$1
  local desktop_dir=$2
  local desktop_file="$desktop_dir/{{ app }}.desktop"
  local content="# $( now_extended )
[Desktop Entry]
Version=1.0
Type=Application
Name={{ app }}
Icon=${app_dir}/rsc/George.png
Exec=\"${app_dir}/george.sh\"
Categories=Education;
Comment=Create. Learn. Think.
Terminal=false
StartupWMClass={{ app }}
"
    if [[ $3 == "sudo" ]]; then
        sudo mkdir -p ${desktop_dir}
        sudo -E bash -c "echo \"${content}\" > ${desktop_file}"
        sudo chmod 644 ${desktop_file}
    else
        mkdir -p ${desktop_dir}
        echo "${content}" > ${desktop_file}
    fi
}
