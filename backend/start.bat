@echo off
echo ========================================
echo TeamMind Backend Startup
echo ========================================

:: 设置百度千帆 API Key
set QIANFAN_API_KEY=bce-v3/ALTAKSP-W8fnSI7P0cqEcBxE6SYuM/94fd0c1e5615d09b244576df2d68dfd0739ed5f2
set QIANFAN_BASE_URL=https://qianfan.baidubce.com/v2/coding

echo.
echo [INFO] Baidu Qianfan API configured
echo [INFO] Starting backend server...
echo.

:: 进入后端目录
cd /d "%~dp0"

:: 检查 Maven
where mvn >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Maven not found. Please install Maven first.
    pause
    exit /b 1
)

:: 启动后端
mvn spring-boot:run

pause
