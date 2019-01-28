:: Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
:: The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
:: By using this software in any fashion, you are agreeing to be bound by the terms of this license.
:: You must not remove this notice, or any other, from this software.

:: https://superuser.com/questions/106848/batch-file-that-runs-cmd-exe-a-command-and-then-stays-open-at-prompt
:: https://www.robvanderwoude.com/escapechars.php

SET java="jre\bin\java.exe"
SET jar="jar\{{ jar-name }}"
SET run=%java% -splash:{{ splash-image }} -Dprism.dirtyopts=false -jar %jar%
CALL :set_ldt

START cmd.exe /K "pushd %~dp0 & echo Available vars: ^%%java^%%, ^%%jar^%%, and ^%%run^%% && TITLE {{ app }} CLI"


:set_ldt
FOR /F "usebackq tokens=1,2 delims==" %%i IN (`wmic os get LocalDateTime /VALUE 2^>NUL`) DO IF '.%%i.'=='.LocalDateTime.' SET ldt=%%j
SET yyyy=%ldt:~0,4%
SET mm=%ldt:~4,2%
SET dd=%ldt:~6,2%
SET H2=%ldt:~8,2%
SET M2=%ldt:~10,2%
SET S2=%ldt:~12,2%
EXIT /B 0
