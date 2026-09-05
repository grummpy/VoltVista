# VoltVista

VoltVista is a local-first electric-vehicle intelligence dashboard powered by a Java database backend. It preserves the project’s required ArrayList storage, stable merge sort, and HashMap indexing while adding a responsive front end, interactive SVG analytics, safe CSV editing, and double-click applications for macOS and Windows.

## Open the app on macOS

1. Unzip `VoltVista-macOS.zip`.
2. Double-click `VoltVista.app`.
3. The dashboard opens in the default browser and loads the bundled CS 508 dataset.

The app includes its own Java runtime. No Java or JavaFX setup is required. Because this educational build is not Apple-notarized, macOS may require Control-clicking the app and choosing **Open** the first time.

## Open the app on Windows

Run `VoltVista-Setup.exe` and follow the installer, or unzip `VoltVista-Windows-Portable.zip` and double-click `VoltVista.exe`. Both distributions include Java, so JavaFX and a separate JDK are not required. Windows SmartScreen may ask for confirmation because this educational build is not code-signed.

## Features

- Six responsive KPIs using weighted EV share
- Interactive SVG pulse meter, bar chart, donut chart, and lightning refresh control
- State then county cascading selection
- Vehicle-primary-use and full-text search filters
- State, county, use, and date grouping
- Paged records table designed for more than 25,000 rows
- Multi-select deletion with confirmation
- Validated record creation from the real 10-column schema
- CSV import and atomic save
- RFC 4180 quoted-field CSV parsing
- Local-only server bound to `127.0.0.1`

## Data location

The working copy is stored in `~/Library/Application Support/VoltVista` on macOS and `%APPDATA%\VoltVista` on Windows. The bundled original dataset remains inside the app.

VoltVista always uses `http://127.0.0.1:47821`, so an existing dashboard tab reconnects after an app restart. Clicking the running app again reopens the dashboard. Diagnostic logs are stored beside the working data as `voltvista.log`.

## Build from source

On macOS, run `chmod +x build.sh run-dev.sh` and then `./build.sh`. On Windows, run `powershell -ExecutionPolicy Bypass -File build-windows.ps1`. JDK 17 or newer with `jpackage` is required only for building from source.

## Architecture

- `src/DataManager.java`: CSV database, ArrayList, merge sort, HashMaps, analytics
- `src/VoltVistaServer.java`: local Java API and desktop launcher
- `web/`: responsive front end and interactive SVG visualizations
- `data/ev_data.csv`: bundled CS 508 dataset
- `assets/voltvista-cover.png`: original app cover artwork
- `legacy/CSVDatabase.java`: preserved original console implementation
