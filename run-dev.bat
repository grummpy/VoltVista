@echo off
setlocal
set "PROJECT_DIR=%~dp0"
if not exist "%PROJECT_DIR%build\dev\web" mkdir "%PROJECT_DIR%build\dev\web"
if not exist "%PROJECT_DIR%build\dev\data" mkdir "%PROJECT_DIR%build\dev\data"
javac --add-modules jdk.httpserver -d "%PROJECT_DIR%build\dev" "%PROJECT_DIR%src\DataManager.java" "%PROJECT_DIR%src\VoltVistaServer.java" || exit /b 1
xcopy /E /I /Y "%PROJECT_DIR%web" "%PROJECT_DIR%build\dev\web" >nul
copy /Y "%PROJECT_DIR%data\ev_data.csv" "%PROJECT_DIR%build\dev\data\ev_data.csv" >nul
java --add-modules jdk.httpserver -cp "%PROJECT_DIR%build\dev" VoltVistaServer
