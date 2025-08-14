@echo off
rem VoxStream Application Launcher Script for Windows
rem Windows 10+ compatible startup script
rem Performs system validation and launches the application with proper error handling

setlocal EnableDelayedExpansion

set "APP_NAME=VoxStream"
set "APP_VERSION=1.0.0-SNAPSHOT"
set "MIN_JAVA_VERSION=17"
set "SCRIPT_DIR=%~dp0"
set "LOG_FILE=%SCRIPT_DIR%logs\launcher.log"

rem Create logs directory if it doesn't exist
if not exist "%SCRIPT_DIR%logs" mkdir "%SCRIPT_DIR%logs"

rem Print banner
echo.
echo ==================================
echo   VoxStream Application Launcher  
echo   Version: %APP_VERSION%
echo ==================================
echo.

rem Log function
call :log "INFO" "VoxStream launcher started"

rem Validate system requirements
call :validate_system
if !errorlevel! neq 0 (
    echo System validation failed. Cannot start VoxStream.
    call :log "ERROR" "System validation failed"
    pause
    exit /b 1
)

rem Build application
call :build_application
if !errorlevel! neq 0 (
    echo Build failed. Cannot start VoxStream.
    call :log "ERROR" "Build failed"
    pause
    exit /b 2
)

rem Launch application
call :launch_application
set "exit_code=!errorlevel!"

call :log "INFO" "VoxStream launcher finished with exit code: !exit_code!"
if !exit_code! neq 0 pause
exit /b !exit_code!

rem ========================================
rem Functions
rem ========================================

:log
    set "level=%~1"
    set "message=%~2"
    set "timestamp=%date% %time%"
    echo [!timestamp!] [!level!] !message! >> "%LOG_FILE%"
    goto :eof

:validate_system
    call :log "INFO" "Validating system requirements..."
    echo Validating system requirements...
    
    rem Get system information
    for /f "tokens=2 delims==" %%i in ('wmic os get version /value ^| find "="') do set "os_version=%%i"
    for /f "tokens=2 delims==" %%i in ('wmic os get osarchitecture /value ^| find "="') do set "arch=%%i"
    
    rem Check Java version
    java -version >nul 2>&1
    if !errorlevel! neq 0 (
        echo [ERROR] Java not found. Please install Java %MIN_JAVA_VERSION%+ from: https://adoptium.net/
        call :log "ERROR" "Java not found"
        exit /b 1
    )
    
    rem Get Java version string
    for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| find "version"') do (
        set "java_version=%%i"
        set "java_version=!java_version:"=!"
    )
    
    echo System Information:
    echo   OS: Windows !os_version!
    echo   Architecture: !arch!
    echo   Java: !java_version!
    echo.
    
    call :log "INFO" "System: Windows !os_version!, Arch: !arch!, Java: !java_version!"
    
    rem Check Java version (basic check for Java 17+)
    echo !java_version! | findstr /r "^1[7-9]\." >nul || echo !java_version! | findstr /r "^[2-9][0-9]\." >nul
    if !errorlevel! neq 0 (
        echo [ERROR] Java %MIN_JAVA_VERSION% or newer is required
        echo    Current Java: !java_version!
        echo    Please install Java %MIN_JAVA_VERSION%+ from: https://adoptium.net/
        call :log "ERROR" "Java version not supported: !java_version!"
        exit /b 1
    )
    
    echo [OK] System requirements validated successfully
    call :log "INFO" "System requirements validation passed"
    goto :eof

:find_maven
    if exist "%SCRIPT_DIR%mvnw.cmd" (
        set "maven_cmd=%SCRIPT_DIR%mvnw.cmd"
        goto :eof
    )
    
    where mvn >nul 2>&1
    if !errorlevel! equ 0 (
        set "maven_cmd=mvn"
        goto :eof
    )
    
    set "maven_cmd="
    goto :eof

:build_application
    call :log "INFO" "Building application..."
    echo Building VoxStream...
    
    call :find_maven
    if "!maven_cmd!"=="" (
        echo [ERROR] Maven not found
        call :log "ERROR" "Maven executable not found"
        exit /b 1
    )
    
    rem Build all modules with Maven (install to local repo)
    "!maven_cmd!" clean install -q > "%LOG_FILE%.build" 2>&1
    if !errorlevel! equ 0 (
        echo [OK] Build completed successfully
        call :log "INFO" "Application build completed"
    ) else (
        echo [ERROR] Build failed
        echo    Check build log: %LOG_FILE%.build
        call :log "ERROR" "Application build failed"
        exit /b 1
    )
    goto :eof

:launch_application
    call :log "INFO" "Launching VoxStream application..."
    echo Starting VoxStream...
    
    call :find_maven
    if "!maven_cmd!"=="" (
        echo [ERROR] Maven not found
        call :log "ERROR" "Maven executable not found"
        exit /b 1
    )
    
    rem Launch JavaFX application
    call :log "INFO" "Executing: !maven_cmd! javafx:run -f voxstream-frontend/pom.xml"
    echo [INFO] Launching VoxStream...
    
    cd /d "%SCRIPT_DIR%"
    "!maven_cmd!" javafx:run -f voxstream-frontend\pom.xml
    
    set "exit_code=!errorlevel!"
    if !exit_code! equ 0 (
        call :log "INFO" "Application exited normally"
    ) else (
        call :log "ERROR" "Application exited with code: !exit_code!"
        echo [ERROR] Application exited with error code: !exit_code!
    )
    
    exit /b !exit_code!
