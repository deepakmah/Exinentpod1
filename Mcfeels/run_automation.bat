@echo off
echo ================================================
echo McFeels E-commerce Automation Testing Framework
echo ================================================
echo.

REM Check if Java is available
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java is not installed or not in PATH
    echo Please install Java 8 or higher and try again
    pause
    exit /b 1
)

REM Set current directory as working directory
cd /d "%~dp0"

echo Current directory: %CD%
echo.

REM Check if config.properties exists
if not exist "config.properties" (
    echo WARNING: config.properties not found!
    echo Using default configuration...
    echo.
)

REM Check if Maven is available and use it, otherwise use direct java execution
mvn -version >nul 2>&1
if not errorlevel 1 (
    echo Using Maven to compile and run...
    echo.
    mvn clean compile exec:java -Dexec.mainClass="Mcfeels.mcfeels" -Dexec.args="%*"
) else (
    echo Maven not found, attempting direct Java execution...
    echo.
    echo Compiling Java files...
    javac -cp "lib/*;." src/main/java/Mcfeels/mcfeels.java
    
    if errorlevel 1 (
        echo ERROR: Compilation failed. Please check dependencies.
        echo Make sure Selenium JAR files are in the 'lib' directory.
        pause
        exit /b 1
    )
    
    echo Running automation...
    echo.
    java -cp "lib/*;src/main/java;." Mcfeels.mcfeels %*
)

echo.
echo ================================================
echo Test execution completed.
echo Check the generated log files and reports.
echo ================================================
pause