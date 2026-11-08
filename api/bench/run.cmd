@echo off
chcp 65001 > nul
rem 商品接口压测：先预热（结果丢弃），再正式压测，结果和 HTML 报告放到 results\<结果名>\
rem 用法（任意目录执行均可）：run.cmd <结果名，如 baseline> [并发数，默认 50] [每个场景秒数，默认 60]
rem JMeter 不在默认位置时，先 set JMETER_HOME=你的 JMeter 目录
setlocal

if "%~1"=="" (
    echo 用法：run.cmd ^<结果名，如 baseline^> [并发数，默认 50] [每个场景秒数，默认 60]
    exit /b 1
)
set "NAME=%~1"
set "THREADS=%~2"
if "%THREADS%"=="" set "THREADS=50"
set "DURATION=%~3"
if "%DURATION%"=="" set "DURATION=60"
if not defined JMETER_HOME set "JMETER_HOME=C:\Jmeter\apache-jmeter-5.6.3"
set "JMETER=%JMETER_HOME%\bin\jmeter.bat"
if not exist "%JMETER%" (
    echo 找不到 %JMETER%，请先 set JMETER_HOME=你的 JMeter 目录
    exit /b 1
)

cd /d "%~dp0"
set "OUT=results\%NAME%"

rem 预热：让后端 JIT 编译、MySQL 把数据读进缓冲池，避免第一轮的冷启动拖低结果
echo [1/2] 预热：10 并发，每个场景 10 秒
call "%JMETER%" -n -t product.jmx -Jthreads=10 -Jduration=10 -l "results\warmup.jtl" -j "results\warmup.log" -f
if errorlevel 1 exit /b 1

echo [2/2] 正式压测：%THREADS% 并发，每个场景 %DURATION% 秒
call "%JMETER%" -n -t product.jmx -Jthreads=%THREADS% -Jduration=%DURATION% -l "%OUT%\result.jtl" -j "%OUT%\jmeter.log" -e -o "%OUT%\report" -f
if errorlevel 1 exit /b 1

echo 报告：%~dp0%OUT%\report\index.html
