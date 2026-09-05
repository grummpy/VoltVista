#!/bin/zsh
set -euo pipefail
SCRIPT_DIR=${0:A:h}
BUILD_DIR="$SCRIPT_DIR/build"
APP_DIR="$BUILD_DIR/app"
DIST_DIR="$SCRIPT_DIR/dist"
PACKAGE_DIR=$(mktemp -d)
mkdir -p "$APP_DIR/web" "$APP_DIR/data" "$DIST_DIR"
javac --add-modules jdk.httpserver -d "$APP_DIR" "$SCRIPT_DIR/src/DataManager.java" "$SCRIPT_DIR/src/VoltVistaServer.java"
cp -R "$SCRIPT_DIR/web/." "$APP_DIR/web/"
cp "$SCRIPT_DIR/data/ev_data.csv" "$APP_DIR/data/ev_data.csv"
jar --create --file "$BUILD_DIR/VoltVista.jar" --main-class VoltVistaServer -C "$APP_DIR" .
xattr -cr "$BUILD_DIR"
jpackage --type app-image --name VoltVista --dest "$PACKAGE_DIR" --input "$BUILD_DIR" \
  --main-jar VoltVista.jar --main-class VoltVistaServer --add-modules java.desktop,jdk.httpserver \
  --description "Electric vehicle intelligence dashboard" --vendor "VoltVista"
ditto -c -k --sequesterRsrc --keepParent "$PACKAGE_DIR/VoltVista.app" "$DIST_DIR/VoltVista-macOS.zip"
echo "Built $DIST_DIR/VoltVista-macOS.zip"
