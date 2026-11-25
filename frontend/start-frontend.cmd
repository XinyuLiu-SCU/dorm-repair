@echo off
setlocal
cd /d "%~dp0"
if exist "..\dorm-repair-env\tools\node-v22.22.1-win-x64\node.exe" set "PATH=%~dp0..\dorm-repair-env\tools\node-v22.22.1-win-x64;%PATH%"
npm.cmd run dev %*
