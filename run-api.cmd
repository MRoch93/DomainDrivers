@echo off
setlocal
pushd "%~dp0"
if errorlevel 1 exit /b 1
call "%~dp0mvnw.cmd" -B -ntp -DskipTests package
if errorlevel 1 goto failed
set "JAVA_CMD=java"
if defined JAVA_HOME set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
"%JAVA_CMD%" -cp "%~dp0target\parallelization-engine-1.0-SNAPSHOT.jar" pl.edu.parallelization.api.ApiMain
set "API_EXIT=%ERRORLEVEL%"
popd
exit /b %API_EXIT%
:failed
popd
exit /b 1
