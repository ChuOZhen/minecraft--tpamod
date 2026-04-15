@echo off
chcp 65001 >nul
title TPA Mod 构建工具
color 0A

echo ========================================
echo    TPA Mod 构建脚本
echo ========================================
echo.

cd /d "%~dp0"

echo 正在构建 TPA Mod...
echo.

gradlew.bat build --no-daemon

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo    构建成功！
    echo ========================================
    echo.
    echo 生成的JAR文件位置:
    echo   build\libs\tpa-1.0.0.jar
    echo.
    echo 请将JAR文件复制到服务器的 mods 文件夹中
    echo.
) else (
    echo.
    echo ========================================
    echo    构建失败！
    echo ========================================
    echo.
    echo 请检查错误信息并修复问题
    echo.
)

pause
