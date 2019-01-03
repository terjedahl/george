:: https://superuser.com/questions/106848/batch-file-that-runs-cmd-exe-a-command-and-then-stays-open-at-prompt
:: https://www.robvanderwoude.com/escapechars.php

set java="jre\bin\java.exe"
set jar="deployable\george-application-{{ version }}-standalone.jar"
set run=%java% -jar %jar%

start cmd.exe /k "pushd %~dp0 & echo Available vars: ^%%java^%%, ^%%jar^%%, and ^%%run^%%"

