@echo off
echo 正在下载Gradle Wrapper...

set GRADLE_WRAPPER_URL=https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar
set WRAPPER_DIR=gradle\wrapper
set WRAPPER_JAR=%WRAPPER_DIR%\gradle-wrapper.jar

if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"

echo 下载地址: %GRADLE_WRAPPER_URL%
echo 目标路径: %WRAPPER_JAR%

powershell -Command "Invoke-WebRequest -Uri '%GRADLE_WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"

if exist "%WRAPPER_JAR%" (
    echo 下载成功!
) else (
    echo 下载失败，尝试备用地址...
    powershell -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.5-bin.zip' -OutFile 'gradle.zip'"
    echo 请手动解压gradle.zip到gradle目录
)

pause
