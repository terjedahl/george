:: Launch a Command prompt in current working directory and prints JAVA_HOME and java -version
:: https://stackoverflow.com/questions/8922224/multiple-commands-on-a-single-line-in-a-windows-batch-file

start cmd.exe /K "pushd %~dp0 & echo JAVA_HOME: %JAVA_HOME%"
