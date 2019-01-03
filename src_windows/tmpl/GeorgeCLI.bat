:: Copyright (c) 2016-2019 Terje Dahl. All rights reserved.
:: The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
:: By using this software in any fashion, you are agreeing to be bound by the terms of this license.
:: You must not remove this notice, or any other, from this software.

:: https://superuser.com/questions/106848/batch-file-that-runs-cmd-exe-a-command-and-then-stays-open-at-prompt
:: https://www.robvanderwoude.com/escapechars.php

set java="jre\bin\java.exe"
set jar="deployable\george-application-{{ version }}-standalone.jar"
set run=%java% -jar %jar%

start cmd.exe /k "pushd %~dp0 & echo Available vars: ^%%java^%%, ^%%jar^%%, and ^%%run^%%"

