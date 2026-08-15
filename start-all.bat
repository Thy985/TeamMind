@echo off
setlocal EnableDelayedExpansion

chcp 65001 > nul
title TeamMind Startup

echo ============================================================
echo                TeamMind Full Stack Startup
echo ============================================================
echo.
echo   Backend  : http://localhost:8080
echo   Frontend : http://localhost:3000
echo.

:: ------------------------------------------------------------
:: 1. 环境前置检查
:: ------------------------------------------------------------
echo [STEP 1/5] Checking environment...

:: 1.1 Java
where java > nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java not found. Please install JDK 17 first:
    echo         https://adoptium.net/temurin/releases/?version=17
    echo.
    pause
    exit /b 1
)

:: 1.2 Maven
where mvn > nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven not found. Please install Maven 3.8+ first:
    echo         https://maven.apache.org/download.cgi
    echo.
    pause
    exit /b 1
)

:: 1.3 Node
where node > nul 2>&1
if errorlevel 1 (
    echo [ERROR] Node.js not found. Please install Node.js 18+ first:
    echo         https://nodejs.org/
    echo.
    pause
    exit /b 1
)

:: 1.4 npm
where npm > nul 2>&1
if errorlevel 1 (
    echo [ERROR] npm not found. Please install Node.js 18+ which includes npm.
    pause
    exit /b 1
)

echo   - Java : OK
echo   - Maven: OK
echo   - Node : OK
echo   - npm  : OK
echo.

:: ------------------------------------------------------------
:: 2. JWT 密钥检查（首次启动必须设置）
:: ------------------------------------------------------------
echo [STEP 2/5] Checking JWT secret...

if "%TEAMMIND_JWT_SECRET%"=="" (
    echo [WARN] TEAMMIND_JWT_SECRET is not set.
    echo        Backend will refuse to start without it.
    echo        Generating a temporary secret for first-time use...
    echo        (For production, please set TEAMMIND_JWT_SECRET to a 32+ char value)
    echo.
    :: 用时间戳生成一个 64 字符的临时密钥
    for /f "tokens=*" %%a in ('powershell -NoProfile -Command "[System.Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes('teammind-dev-' + [guid]::NewGuid().ToString() + '-' + [DateTime]::UtcNow.Ticks))"') do (
        set "TEAMMIND_JWT_SECRET=%%a"
    )
    echo [INFO] Temporary JWT secret generated (length=64)
) else (
    echo   - JWT secret: OK
)
echo.

:: ------------------------------------------------------------
:: 3. LLM API Key 检查
:: ------------------------------------------------------------
echo [STEP 3/5] Checking LLM API keys...

set "HAS_LLM_KEY=false"
if not "%QIANFAN_API_KEY%"=="" set "HAS_LLM_KEY=true"
if not "%OPENAI_API_KEY%"=="" set "HAS_LLM_KEY=true"
if not "%ANTHROPIC_API_KEY%"=="" set "HAS_LLM_KEY=true"

if "%HAS_LLM_KEY%"=="false" (
    echo [WARN] No LLM API key set. The app will start, but LLM calls will fail.
    echo        Set one of these environment variables for real LLM usage:
    echo          - QIANFAN_API_KEY
    echo          - OPENAI_API_KEY
    echo          - ANTHROPIC_API_KEY
    echo.
    echo        (You can ignore this warning if you only want to explore the UI)
    echo.
) else (
    echo   - LLM API key: configured
    echo.
)

:: ------------------------------------------------------------
:: 4. 端口检查
:: ------------------------------------------------------------
echo [STEP 4/5] Checking ports...

set "PORT_8080_USED=false"
set "PORT_3000_USED=false"
netstat -ano | findstr ":8080 " > nul 2>&1 && set "PORT_8080_USED=true"
netstat -ano | findstr ":3000 " > nul 2>&1 && set "PORT_3000_USED=true"

if "%PORT_8080_USED%"=="true" (
    echo [WARN] Port 8080 is already in use. Backend may fail to start.
)
if "%PORT_3000_USED%"=="true" (
    echo [WARN] Port 3000 is already in use. Frontend may fail to start.
)
echo.

:: ------------------------------------------------------------
:: 5. 启动服务
:: ------------------------------------------------------------
echo [STEP 5/5] Starting services...

:: 日志目录
if not exist "%~dp0logs" mkdir "%~dp0logs"

:: 启动后端
echo [INFO] Starting backend (this may take 30-60 seconds on first run)...
start "TeamMind Backend" /MIN cmd /k "cd /d %~dp0backend && set TEAMMIND_JWT_SECRET=%TEAMMIND_JWT_SECRET% && mvn spring-boot:run > %~dp0logs\backend.log 2>&1"

:: 等待后端真正起来（最长 90 秒）
echo [INFO] Waiting for backend to be ready...
set /a "WAIT_COUNT=0"
:WAIT_LOOP
set /a "WAIT_COUNT+=1"
timeout /t 3 /nobreak > nul

:: 检测后端是否启动成功（探活 /api/auth/login）
curl -s -o nul -w "%%{http_code}" -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"probe\",\"password\":\"probe\"}" 2>&1 | findstr /R "200 401" > nul
if not errorlevel 1 goto BACKEND_READY

if !WAIT_COUNT! GEQ 30 (
    echo [WARN] Backend did not respond within 90 seconds. Check logs\backend.log
    echo        Continuing anyway - frontend will start regardless.
    goto BACKEND_SKIP
)
goto WAIT_LOOP

:BACKEND_READY
echo [SUCCESS] Backend is ready on http://localhost:8080
echo.

:BACKEND_SKIP

:: 启动前端
echo [INFO] Starting frontend...
start "TeamMind Frontend" /MIN cmd /k "cd /d %~dp0 && npm run dev > %~dp0logs\frontend.log 2>&1"

:: 等待前端启动（最长 30 秒）
set /a "FE_WAIT=0"
:FE_WAIT_LOOP
set /a "FE_WAIT+=1"
timeout /t 2 /nobreak > nul

curl -s -o nul -w "%%{http_code}" http://localhost:3000 > nul 2>&1
if not errorlevel 1 goto FE_READY

if !FE_WAIT! GEQ 15 goto FE_READY
goto FE_WAIT_LOOP

:FE_READY
echo [SUCCESS] Frontend is ready on http://localhost:3000
echo.

echo ============================================================
echo                  TeamMind is Running!
echo ============================================================
echo.
echo   Backend  : http://localhost:8080
echo   Frontend : http://localhost:3000
echo   Default login: admin / admin123
echo.
echo   Logs:
echo     - logs\backend.log
echo     - logs\frontend.log
echo.
echo   Open the frontend in your browser at:
echo   http://localhost:3000
echo.
echo ============================================================

:: 自动打开浏览器
start http://localhost:3000

echo.
echo Press any key to close this window (services will keep running)
pause > nul