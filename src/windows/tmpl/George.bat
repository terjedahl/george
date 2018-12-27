:: https://superuser.com/questions/106848/batch-file-that-runs-cmd-exe-a-command-and-then-stays-open-at-prompt

start cmd.exe /C "pushd %~dp0 & jre\bin\java.exe -jar deployable\george-application-{{ version }}-standalone.jar"
