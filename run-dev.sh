#!/bin/zsh
set -euo pipefail
SCRIPT_DIR=${0:A:h}
mkdir -p "$SCRIPT_DIR/build/dev/web" "$SCRIPT_DIR/build/dev/data"
javac --add-modules jdk.httpserver -d "$SCRIPT_DIR/build/dev" "$SCRIPT_DIR/src/DataManager.java" "$SCRIPT_DIR/src/VoltVistaServer.java"
cp -R "$SCRIPT_DIR/web/." "$SCRIPT_DIR/build/dev/web/"
cp "$SCRIPT_DIR/data/ev_data.csv" "$SCRIPT_DIR/build/dev/data/ev_data.csv"
java --add-modules jdk.httpserver -cp "$SCRIPT_DIR/build/dev" VoltVistaServer
