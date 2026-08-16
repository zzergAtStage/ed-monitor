@echo off
setlocal enabledelayedexpansion
set JAVA_HOME=C:\Users\Father\.jdks\temurin-25.0.1
set PATH=!JAVA_HOME!\bin;%PATH%
cd /d "C:\App\ed-monitor"
start "" javaw.exe -Ded.server.baseUrl=http://localhost:50080 -jar "C:\App\ed-monitor\ed-monitor-swing\target\ed-monitor-swing-0.3.1.jar"
exit /b 0