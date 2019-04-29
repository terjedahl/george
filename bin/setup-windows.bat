@echo off

rem set bin_dir=%~dp0
rem set base_dir=%bin_dir:~0,-4%
rem echo BASE_DIR %base_dir%

set lib_dir=javafx-libs\Windows

echo Ensuring dir '%lib_dir%' ...
mkdir %lib_dir% 2> nul

echo Downloading JavaFX SDK ...
curl --output %lib_dir%\sdk.zip -L https://gluonhq.com/download/javafx-12-0-1-sdk-windows
echo Unzipping ...
java bin\unzip.java %lib_dir%\sdk.zip %lib_dir%
del /Q %lib_dir%\sdk.zip

echo Downloading JavaFX jmods ...
curl --output %lib_dir%\jmods.zip -L https://gluonhq.com/download/javafx-12-0-1-jmods-windows
echo Unzipping ...
java bin\unzip.java %lib_dir%\jmods.zip %lib_dir%
del /Q %lib_dir%\jmods.zip

dir %lib_dir%