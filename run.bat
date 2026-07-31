@echo off
chcp 65001 >nul
title HsiaoWear 一键编译安装运行

:: ================================================================
:: HsiaoWear 一键编译 + 安装 + 启动 + 日志
:: 用法：双击运行，或在终端里直接拖入此文件回车
:: ================================================================

setlocal enabledelayedexpansion

:: ---------- 配置 ----------
:: 如果 applicationId 改了，这里也要改
set PACKAGE_NAME=com.example.hsiaopu
set MAIN_ACTIVITY=.MainActivity

:: 日志过滤标签（多个用空格分隔，供 findstr 使用）
set LOG_FILTER=Hsiaopu VoskSpeechHelper TtsManager

:: ---------- 颜色输出 ----------
set ESC=
set COLOR_GREEN=%ESC%[92m
set COLOR_YELLOW=%ESC%[93m
set COLOR_RED=%ESC%[91m
set COLOR_CYAN=%ESC%[96m
set COLOR_RESET=%ESC%[0m

:: ============================
echo %COLOR_CYAN%=========================================%COLOR_RESET%
echo %COLOR_CYAN%  HsiaoWear 一键编译安装运行%COLOR_RESET%
echo %COLOR_CYAN%=========================================%COLOR_RESET%
echo.

:: ============================
:: 第一步：编译 + 安装（一步到位）
:: ============================
echo %COLOR_YELLOW%[1/3] 正在编译并安装到设备 ...%COLOR_RESET%
echo.

call gradlew.bat installDebug
if %ERRORLEVEL% neq 0 (
    echo.
    echo %COLOR_RED%[失败] 编译或安装出错%COLOR_RESET%
    echo   可能原因：
    echo     1. 编译出错，请查看上方错误详情
    echo     2. 没有连接 Android 设备或模拟器
    echo     3. 设备未开启 USB 调试
    echo.
    echo 请先用 adb devices 检查设备连接状态
    echo 按任意键退出 & pause >nul
    exit /b %ERRORLEVEL%
)
echo.
echo %COLOR_GREEN%[完成] 编译并安装成功%COLOR_RESET%

:: ============================
:: 第二步：清空旧日志，然后启动 App
:: ============================
echo %COLOR_YELLOW%[2/3] 正在启动 App ...%COLOR_RESET%

adb logcat -c
adb shell am start -n %PACKAGE_NAME%/%PACKAGE_NAME%%MAIN_ACTIVITY%
echo %COLOR_GREEN%[完成] 已启动%COLOR_RESET%

:: ============================
:: 第三步：查看日志
:: ============================
echo.
echo %COLOR_YELLOW%[3/3] 正在显示日志（仅显示本应用相关）%COLOR_RESET%
echo %COLOR_CYAN%------------------------------------------------%COLOR_RESET%
echo  日志已启动，按 Ctrl+C 可以退出查看。
echo  日志过滤关键词：%LOG_FILTER%
echo %COLOR_CYAN%------------------------------------------------%COLOR_RESET%
echo.

:: 显示本应用的日志（通过 findstr 过滤）
adb logcat -v brief | findstr /i "%LOG_FILTER%"

:: 如果用户按了 Ctrl+C 退出 logcat，会走到这里
echo.
echo %COLOR_GREEN%所有操作已完成%COLOR_RESET%
pause
