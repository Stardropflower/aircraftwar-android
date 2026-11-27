@echo off
chcp 65001 >nul
setlocal
title 飞机大战 - 对战服务端
cd /d "%~dp0"
if not defined PORT set "PORT=8765"
if not "%~1"=="" set "PORT=%~1"

echo ================================================================
echo    飞机大战 Android 版  -  网络对战服务端（一键启动）
echo ================================================================
echo.

where node >nul 2>nul
if errorlevel 1 (
    echo [错误] 没有找到 Node.js
    echo.
    echo        请先安装 Node.js：https://nodejs.org  选 LTS 版本，一路下一步
    echo        装好后关掉本窗口，重新双击本脚本。
    echo.
    pause
    exit /b 1
)

for /f "delims=" %%v in ('node -v 2^>nul') do set "NODEVER=%%v"
echo [1/3] Node.js %NODEVER% 已就绪

if exist "node_modules\ws" goto deps_ok
echo [2/3] 首次运行，正在安装依赖，约 10 秒，只需一次...
call npm.cmd install --no-fund --no-audit
if errorlevel 1 (
    echo.
    echo [错误] 依赖安装失败，请检查网络后重试。
    echo        可换国内镜像：npm config set registry https://registry.npmmirror.com
    pause
    exit /b 1
)
goto deps_ready

:deps_ok
echo [2/3] 依赖已就绪

:deps_ready
netstat -ano | findstr ":%PORT%" | findstr /i "LISTENING" >nul 2>nul
if not errorlevel 1 (
    echo.
    echo [提示] 端口 %PORT% 已被占用。
    echo        如果服务端已经在另一个窗口跑着，那就不用再开了，直接让手机连过去。
    echo        否则请关掉占用它的程序，或换个端口重开：
    echo            start-server.bat 8766
    echo            手机上的服务器地址端口也改成 8766
    echo.
    pause
    exit /b 1
)

echo [3/3] 启动服务端，端口 %PORT%
echo.
echo ---------- 手机里「服务器地址」填下面这一条 ----------
powershell -NoProfile -Command "$idx=(Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue | Sort-Object { $_.RouteMetric + $_.InterfaceMetric } | Select-Object -First 1).InterfaceIndex; Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notmatch '^(127\.|169\.254\.)' } | ForEach-Object { $tag=''; if ($_.InterfaceIndex -eq $idx) { $tag='   (手机填这个)' }; '      ws://' + $_.IPAddress + ':' + $env:PORT + '   [' + $_.InterfaceAlias + ']' + $tag }" 2>nul
echo.
echo   标了「手机填这个」的那条就是，其余可能是虚拟网卡或 VPN，别用。
echo   手机必须和电脑连同一个 WiFi。
echo   手机连不上，多半是电脑防火墙拦了 %PORT% 端口。
echo.
echo ---------- 下面出现「已启动」就成功了，这个窗口别关 ----------
echo.

node server.js
echo.
echo [服务已停止]
echo 如果上面有红色报错，把这些内容发我。
pause