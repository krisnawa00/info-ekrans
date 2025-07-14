@echo off
echo Talsu novada Info Ekrans - Startejana...

REM Parbaudit vai Java ir instaleta
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo KLJUDA: Java nav instaleta vai nav pieejama PATH!
    echo Ludzu instalējiet Java 11 vai jaunaku versiju.
    pause
    exit /b 1
)

REM Izveidot images mapi, ja nepastav
if not exist "images" mkdir images

REM Parbaudit vai ir jar fails
if not exist "target\info-ekrani-1.0-SNAPSHOT.jar" (
    echo KLJUDA: Aplikacijas fails nav atrasts!
    echo Ludzu kompilejiet projektu ar: mvn clean package
    pause
    exit /b 1
)

REM Meklet JavaFX SDK
set JAVAFX_PATH=
if exist "javafx-sdk-17.0.2\lib" set JAVAFX_PATH=javafx-sdk-17.0.2\lib
if exist "javafx-sdk\lib" set JAVAFX_PATH=javafx-sdk\lib
if exist "lib\javafx" set JAVAFX_PATH=lib\javafx

REM Starte aplikaciju ar JavaFX
echo Starte Info Ekrans aplikaciju...
if defined JAVAFX_PATH (
    echo Izmanto JavaFX no: %JAVAFX_PATH%
    java --module-path %JAVAFX_PATH% --add-modules javafx.controls,javafx.fxml -jar target\info-ekrani-1.0-SNAPSHOT.jar
) else (
    echo Megina starte bez JavaFX moduljiem...
    java -jar target\info-ekrani-1.0-SNAPSHOT.jar
)

REM Ja aplikacija beidz darbu ar kljudu
if %errorlevel% neq 0 (
    echo.
    echo ===============================================
    echo KLJUDA: Aplikacija nevareja starte!
    echo.
    echo Iespejamie risinajumi:
    echo 1. Lejupieladejiet JavaFX SDK no: https://gluonhq.com/products/javafx/
    echo 2. Izpakojiet to saja mape ka 'javafx-sdk-17.0.2'
    echo 3. Vai instalējiet Java 8 ar iebuvetu JavaFX
    echo 4. Vai izmantojiet start-java8.bat
    echo ===============================================
    pause
)

echo Aplikacija apstajas.
pause
