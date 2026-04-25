@echo off
setlocal
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
javac -d out src\backend\*.java src\frontend\*.java || exit /b 1
java -cp out Launcher
endlocal
