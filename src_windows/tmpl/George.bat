:: Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
:: The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
:: By using this software in any fashion, you are agreeing to be bound by the terms of this license.
:: You must not remove this notice, or any other, from this software.

@ECHO OFF

TITLE Launching {{ app }} ...

SET logdir=%TEMP%\{{ app }}
IF NOT EXIST %logdir% MKDIR %logdir%

CALL :set_ldt
SET logfile=%logdir%\{{ app }}_%yyyy%%mm%%dd%T%H2%%M2%%S2%.log

ECHO START: [%yyyy%-%mm%-%dd% %H2%:%M2%:%S2%] >> %logfile%
ECHO. >> %logfile%

START /B jre\bin\javaw.exe -splash:{{ splash-image }} -jar jar\{{ jar-name }} >> %logfile% 2>&1


:set_ldt
FOR /F "usebackq tokens=1,2 delims==" %%i IN (`wmic os get LocalDateTime /VALUE 2^>NUL`) DO IF '.%%i.'=='.LocalDateTime.' SET ldt=%%j
SET yyyy=%ldt:~0,4%
SET mm=%ldt:~4,2%
SET dd=%ldt:~6,2%
SET H2=%ldt:~8,2%
SET M2=%ldt:~10,2%
SET S2=%ldt:~12,2%
EXIT /B 0
