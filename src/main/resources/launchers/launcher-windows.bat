@echo off
setlocal enabledelayedexpansion
set DIR=%~dp0
set RUNTIME=%DIR%..\runtime
set APP=%DIR%..\app
set FLAGS=${FLAGS_PLACEHOLDER}
"%RUNTIME%\bin\java.exe" %FLAGS% -jar "%APP%\app.jar" %*