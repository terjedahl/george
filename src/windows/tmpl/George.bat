:: https://stackoverflow.com/questions/5909012/windows-batch-script-launch-program-and-exit-console
@echo off
start "Launch George" "jre\bin\javaw.exe" "-jar" "deployable\george-application-{{ version }}-standalone.jar"
