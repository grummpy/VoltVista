$ErrorActionPreference = "Stop"
$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BuildDir = Join-Path $ProjectDir "build-windows"
$AppDir = Join-Path $BuildDir "app"
$DistDir = Join-Path $ProjectDir "dist-windows"

New-Item -ItemType Directory -Force -Path $AppDir, "$AppDir\web", "$AppDir\data", $DistDir | Out-Null
javac --add-modules jdk.httpserver -d $AppDir "$ProjectDir\src\DataManager.java" "$ProjectDir\src\VoltVistaServer.java"
Copy-Item "$ProjectDir\web\*" "$AppDir\web" -Recurse -Force
Copy-Item "$ProjectDir\data\ev_data.csv" "$AppDir\data\ev_data.csv" -Force
jar --create --file "$BuildDir\VoltVista.jar" --main-class VoltVistaServer -C $AppDir .

$ImageOutput = Join-Path $env:TEMP ("VoltVista-" + [guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $ImageOutput | Out-Null
jpackage --type app-image --name VoltVista --dest $ImageOutput --input $BuildDir `
  --main-jar VoltVista.jar --main-class VoltVistaServer `
  --add-modules java.desktop,jdk.httpserver `
  --description "Electric vehicle intelligence dashboard" --vendor "VoltVista"
Compress-Archive -Path "$ImageOutput\VoltVista" -DestinationPath "$DistDir\VoltVista-Windows-Portable.zip" -Force

jpackage --type exe --name VoltVista --dest $DistDir --input $BuildDir `
  --main-jar VoltVista.jar --main-class VoltVistaServer `
  --add-modules java.desktop,jdk.httpserver `
  --description "Electric vehicle intelligence dashboard" --vendor "VoltVista" `
  --win-menu --win-shortcut --win-dir-chooser `
  --app-version 1.0.2

Get-ChildItem $DistDir
