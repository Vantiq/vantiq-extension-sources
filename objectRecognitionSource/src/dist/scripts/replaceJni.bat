@echo off
::Save relevant file locations
SETLOCAL
SET targetFile=%~dpn1
for %%X in (%~dp0..\lib\libtensorflow_jni*) do (SET jarFile=%%~fX)

::echo %jarFile%

::Create an move to a temporary directory
mkdir tmp
pushd tmp

::Extract the jar to the current location (the new 'temp' dir)
jar xf %jarFile%

::Copy the target file to each OS site
::I could figure out which OS this is on, but the compilation
::is computer specific, so it wouldn't matter anyways
COPY %targetFile% /B org\tensorflow\native\darwin-x86_64\ /B
COPY %targetFile% /B org\tensorflow\native\linux-x86_64\ /B
COPY %targetFile% /B org\tensorflow\native\windows-x86_64\ /B

::Recreate the jar with the new .so/.dll
jar cf %jarFile% *

::Move back to the original directory
popd

::Remove the temporary directory
rmdir /s/q tmp