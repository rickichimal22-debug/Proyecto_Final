@echo off
cd /d "%~dp0"
javac -encoding UTF-8 Analex.java Anasin.java
if errorlevel 1 exit /b 1
java Analex %*
