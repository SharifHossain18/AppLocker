@echo off
SETLOCAL

echo ============================================
echo   AppLocker - Build Script
echo ============================================
echo.

:: Set up environment
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set ANDROID_HOME=%USERPROFILE%\AppData\Local\Android\Sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%

echo [1/4] Installing JavaScript dependencies...
cd /d "%~dp0"
call npm install
if %errorlevel% neq 0 (
    echo ERROR: npm install failed
    pause
    exit /b 1
)
echo Done.
echo.

echo [2/4] Bundling React Native JS...
call npx react-native bundle --platform android --dev false --entry-file index.js --bundle-output android/app/src/main/assets/index.android.bundle --assets-dest android/app/src/main/res
if %errorlevel% neq 0 (
    echo ERROR: Bundle failed
    pause
    exit /b 1
)
echo Done.
echo.

echo [3/4] Accepting SDK licenses...
if not exist "%ANDROID_HOME%\licenses" mkdir "%ANDROID_HOME%\licenses"
echo. > "%ANDROID_HOME%\licenses\android-sdk-license"
echo 24333f8a63b6825ea9c5514f83c2829b004d1fee >> "%ANDROID_HOME%\licenses\android-sdk-license"
echo. > "%ANDROID_HOME%\licenses\android-sdk-preview-license"
echo 84831b9409646a918e30573bab4c9c91346d8abd >> "%ANDROID_HOME%\licenses\android-sdk-preview-license"
echo Done.
echo.

echo [4/4] Building Android APK (debug)...
cd android
call gradlew.bat assembleDebug
if %errorlevel% neq 0 (
    echo ERROR: Build failed
    pause
    exit /b 1
)
echo.
echo ============================================
echo   BUILD SUCCESS!
echo   APK location: android\app\build\outputs\apk\debug\app-debug.apk
echo ============================================
pause
