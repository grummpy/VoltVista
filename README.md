# VoltVista

VoltVista is a local-first electric-vehicle intelligence dashboard powered by a Java database backend. It preserves the project’s required ArrayList storage, stable merge sort, and HashMap indexing while adding a responsive front end, interactive SVG analytics, safe CSV editing, and a double-click macOS application.

## Open the app

1. Unzip `VoltVista-macOS.zip`.
2. Double-click `VoltVista.app`.
3. The dashboard opens in the default browser and loads the bundled CS 508 dataset.

The app includes its own Java runtime. No Java or JavaFX setup is required. Because this educational build is not Apple-notarized, macOS may require Control-clicking the app and choosing **Open** the first time.

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

The working copy is stored at `~/Library/Application Support/VoltVista/ev_data.csv`. The bundled original dataset remains inside the app.

## Build from source

Run `chmod +x build.sh run-dev.sh` and then `./build.sh` with JDK 17 or newer. The app image and ZIP are written to `dist/`.

## Architecture

- `src/DataManager.java`: CSV database, ArrayList, merge sort, HashMaps, analytics
- `src/VoltVistaServer.java`: local Java API and desktop launcher
- `web/`: responsive front end and interactive SVG visualizations
- `data/ev_data.csv`: bundled CS 508 dataset
- `assets/voltvista-cover.png`: original app cover artwork
- `src/CSVDatabaseOriginal.java`: preserved original console implementation
