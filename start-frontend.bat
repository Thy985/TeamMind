@echo off
echo ========================================
echo TeamMind Frontend Startup
echo ========================================

:: 进入前端目录
cd /d "%~dp0"

:: 检查 Node.js
where node >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Node.js not found. Please install Node.js first.
    pause
    exit /b 1
)

:: 检查依赖
if not exist "node_modules" (
    echo [INFO] Installing dependencies...
    npm install
)

echo.
echo [INFO] Starting frontend server...
echo [INFO] Frontend will run at http://localhost:3000
echo.

:: 启动前端
npm run dev

pause
