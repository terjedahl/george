#!/usr/bin/env bash

# Build the bundle

mkdir target/macos &> /dev/null
rm -rf target/macos/George.app
cp -a src_macos/George.app target/macos/George.app
cp -a target/jre target/macos/George.app/Contents/jre

mkdir target/macos/George.app/Contents/jar &> /dev/null
cp target/uberjar/*standalone.jar target/macos/George.app/Contents/jar/


# Build the installer
# http://juusosalonen.com/post/139067064745/demystifying-pkgbuild-in-os-x

mkdir -p target/macos/root/Applications &> /dev/null
cp -af target/macos/George.app target/macos/root/Applications/George.app
rm -f target/macos/George.pkg &> /dev/null
pkgbuild --root target/macos/root target/macos/George.pkg
rm -rf target/macos/root