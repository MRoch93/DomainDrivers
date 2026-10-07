@echo off
setlocal
rem Wszystkie wzgledne sciezki odnosza sie do katalogu tego skryptu.
pushd "%~dp0"
if errorlevel 1 exit /b 1
set "INPUT=%CD%\input.json"
set "OUTPUT=%CD%\output.json"
if "%~1"=="" goto argumentsReady
if "%~2"=="" goto usage
if not "%~3"=="" goto usage
set "INPUT=%~f1"
set "OUTPUT=%~f2"
:argumentsReady
if /I "%INPUT%"=="%OUTPUT%" goto sameFile
call :removeOutput
if errorlevel 1 goto cleanupFailed
call "%~dp0mvnw.cmd" -B -ntp -DskipTests package
if errorlevel 1 goto failed
set "JAVA_CMD=java"
if defined JAVA_HOME set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
"%JAVA_CMD%" -jar "%~dp0target\parallelization-engine-1.0-SNAPSHOT.jar" "%INPUT%" "%OUTPUT%"
if errorlevel 1 goto failed
popd
exit /b 0
:failed
call :removeOutput
popd
exit /b 1
:sameFile
echo Blad: input i output musza byc roznymi plikami. >&2
goto failedWithoutCleanup
:cleanupFailed
echo Blad: nie mozna usunac poprzedniego output. >&2
goto failedWithoutCleanup
:usage
echo Uzycie: run.cmd [input.json output.json] >&2
:failedWithoutCleanup
popd
exit /b 1
:removeOutput
rem LiteralPath zapobiega interpretowaniu nazwy jako wzorca plikow.
powershell -NoProfile -Command "$ErrorActionPreference='Stop'; if (Test-Path -LiteralPath $env:OUTPUT -PathType Container) { throw 'Output wskazuje katalog'; }; if (Test-Path -LiteralPath $env:OUTPUT) { Remove-Item -LiteralPath $env:OUTPUT -Force; }"
exit /b %ERRORLEVEL%
