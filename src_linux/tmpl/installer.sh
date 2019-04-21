#!/usr/bin/env bash


set -eo pipefail


LINK_NAME="{{ app|lower }}"
THIS_DIR=$( dirname "$0" )

source "${THIS_DIR}/bin/utils.sh"

install_type=1
opt_dir="${HOME}/.local/share/opt"
bin_dir="${HOME}/.local/share/bin"
desktop_dir="${HOME}/.local/share/applications"
action="<action>"
as="normal"  # or "sudo"


_as_sudo() {
    if [[ ${as} == "sudo" ]]; then return 0; else return 1; fi
}


_debug() {
    echo "[DEBUG]: ${@}"
}


_info() {
    echo "
[$action]: ${@}"
}


_print_about_install() {
    echo "
George can be installed as:
  1) for user:    Only you (${USER}) will have access to it. [Default]
  2) for machine: All users on this machine can use it. (Requires 'sudo' privileges)
"
}


_print_about_uninstall() {
    echo "
Unistall will remove all installed files - for either \"user\" or \"machine\" based on location of script:
  - Application code:   '\$HOME/.local/share/opt/{{ app }}'
                        '/opt/{{ app }}'
  - Desktop file:       '\$HOME/.local/share/applications/{{ app }}.desktop'
                        '/usr/share/applications/{{ app }}.desktop'
  - Symlink:            '\$HOME/.local/share/bin/${LINK_NAME}'
                        '/usr/local/bin/${LINK_NAME}'
Uninstall will *not* remove:
  - User's files in:    '\$HOME/{{ app }}'
  - User's settings in: '\$HOME/.local/{{ app }}'
"
}


_get_set_install_type() {
    read -p "Install for user or machine? (1|2) > " type
    case ${type} in
        2|"machine"|"m")  _info "Install for machine";   install_type=2 ;;
        *)                _info "Install for user ${USER}" ;;
    esac
}


_try_clean_opt() {
    local app_dir="${opt_dir}/{{ app }}"

    if [[ -d ${app_dir} ]]; then
        _info "Delete dir '{{ app }}' from '$opt_dir'"
        if _as_sudo; then sudo rm -Rf ${app_dir}; else rm -Rf  ${app_dir}; fi
    else
        _info "No dir '${app_dir}' to delete"
    fi
}


_install_opt() {
    _info "Copy '${THIS_DIR}' to '${opt_dir}'"
    if _as_sudo; then
        sudo mkdir -p ${opt_dir}
        sudo cp -R ${THIS_DIR} ${opt_dir}
        sudo chmod 755 "${opt_dir}/{{ app }}"
    else
        mkdir -p ${opt_dir}
        cp -R ${THIS_DIR} ${opt_dir}
        chmod 755 "${opt_dir}/{{ app }}"
    fi
}


_try_clean_desktop() {
    local desktop_file="${desktop_dir}/{{ app }}.desktop"

    if [[ -f "${desktop_file}" ]]; then
        _info "Delete file '${desktop_file}'"
        if _as_sudo; then sudo rm  ${desktop_file}; else rm  ${desktop_file}; fi
    else
        _info "No file '${desktop_file}' to delete"
    fi
}


_install_desktop() {
    _info "Write file '{{ app }}.desktop' to '${desktop_dir}'"
    if _as_sudo; then
        sudo mkdir -p ${desktop_dir}
        write_desktop_file "${opt_dir}/{{ app }}" ${desktop_dir} "sudo"
    else
        mkdir -p ${desktop_dir}
        write_desktop_file "${opt_dir}/{{ app }}" ${desktop_dir}
    fi
}


_try_clean_bin() {
    local link="${bin_dir}/${LINK_NAME}"

    if [[ -h ${link} ]]; then
        _info "Delete link '${link}'"
        if _as_sudo; then sudo rm ${link}; else rm ${link}; fi
    else
        _info "No link '${link}' to delete"
    fi
}


_install_bin() {
    local link="${bin_dir}/${LINK_NAME}"
    local target="${opt_dir}/{{ app }}/george.sh"

    _info "Make link '${LINK_NAME}' in '${bin_dir}'"
    if _as_sudo; then
        sudo mkdir -p ${bin_dir}
        sudo ln -sfn ${target} ${link}
        sudo chmod 755 ${link}
    else
        mkdir -p ${bin_dir}
        ln -sfn ${target} ${link}
        chmod 755 ${link}
    fi
}


_append_bashrc() {
    local bashrc="${HOME}/.bashrc"
    local new_path="${bin_dir}:\$PATH"
    local append_line="export PATH=${new_path}"

    if [[ -e ${bashrc} ]] &&  egrep -q "export PATH=.*${bin_dir}.*" ${bashrc}; then
        _info "Found '${bin_dir}' already appended to PATH in '${bashrc}'"
    else
        _info "Append \"${append_line}\" in file '${bashrc}'"
        # Users will not be happy if we mess up their original .bashrc file!
        if [[ -e ${bashrc} ]]; then cp ${bashrc} "${bashrc}.backup"; fi
        echo  ${append_line} >> ${bashrc}
    fi
}


_install() {
    case ${install_type} in
        2)
            opt_dir="/opt"
            desktop_dir="/usr/share/applications"
            bin_dir="/usr/local/bin"
            as="sudo"
            _try_clean_opt
            _install_opt
            _install_desktop
            _install_bin
            as="normal"
        ;;
        *)
            _try_clean_opt
            _install_opt
            _install_desktop
            _install_bin
            _append_bashrc
        ;;
    esac
}


_print_post_install() {
    echo "

{{ app }} installed.

You can launch it at any time:
  1. Finding it amongst your desktop applications and clicking on it.
  2. In a terminal[*] do the command '{{ app|lower }}'

     [*]: In a fresh terminal/shell, or in this one if you first do 'source ~/.bashrc' after this installation.

Enjoy!

"
}


_print_post_uninstall() {
    echo "

  {{ app }} uninstalled.

  To get and (re-)install the latest version, visit the \"Download George\" page at:
    https://www.george.andante.no
"
}


_launch() {
    read -p "Do you want to launch {{ app }} now? (y|n) > " yes
    case ${yes} in
        "y"|"Y"|"yes"|"1") _info "Launch"; ${LINK_NAME} & ;;
    esac
}


install() {
    _print_about_install
    _get_set_install_type
    _install
    _print_post_install
    _launch
}


_infer_set_install_type() {
    case ${THIS_DIR} in
        "/opt/{{ app }}")         install_type=2;  _info "Inferred uninstall for machine (requires sudo privileges)" ;;
        "${opt_dir}/{{ app }}") install_type=1;  _info "Inferred uninstall for user ${USER}" ;;
        *)                        install_type=0   _info "Inferred uninstall failed. Exit with -1."; exit -1;;
    esac
}


_confirm_uninstall() {
read -p "
Do you want to proceed with the uninstall? (y|n) > " yes
    case ${yes} in
        "y"|"Y"|"yes"|"1") ;;
        *)                 _info "Uninstall aborted."; exit 0 ;;
    esac

}


_uninstall() {
    case ${install_type} in
        2)
            opt_dir="/opt"
            desktop_dir="/usr/share/applications"
            bin_dir="/usr/local/bin"
            as="sudo"
            _try_clean_opt
            _try_clean_desktop
            _try_clean_bin
            as="normal"
        ;;
        *)
            _try_clean_opt
            _try_clean_desktop
            _try_clean_bin
        ;;
    esac

}


uninstall() {
    _infer_set_install_type
    _print_about_uninstall
    _confirm_uninstall
    _uninstall
    _print_post_uninstall
}


desktop() {
    local app_dir=$( readlink --canonicalize ${THIS_DIR} )
    _info "Write '{{ app }}.desktop' to '${desktop_dir}' for '${app_dir}'"
    mkdir -p ${desktop_dir}
    write_desktop_file ${app_dir} ${desktop_dir}
    echo
}


help() {
    if [[ ${VIA_GEORGE} == "1" ]]; then
        echo "
Usage: ${LINK_NAME} installer <uninstall|desktop>

  'uninstall'  Confirm uninstall, then, based on location, removes application, desktop-file, and symlink.
  'desktop'    Creates a desktop-file for user linked to the symlink target location.
"
    else
        echo "
Usage: $1 <install|uninstall|desktop>

  'install'    Ask if for user or machine, and installs application, desktop-file, and symlink in bin.
  'uninstall'  Confirm uninstall, then, based on location, removes application, desktop-file, and symlink.
  'desktop'    Creates a desktop-file for user linked to the symlink target location.
"
    fi
}


main() {
    case $1 in
        "install")   action="Install";   install   ;;
        "uninstall") action="Uninstall"; uninstall ;;
        "desktop")   action="Desktop";   desktop   ;;
        *)           action="Help";      help $0   ;;
    esac
}


main $@