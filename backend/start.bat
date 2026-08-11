@echo off
echo ========================================
echo TeamMind Backend Startup
echo ========================================

:: 请先设置环境变量 QIANFAN_API_KEY（勿在脚本中硬编码真实 Key）
:: set QIANFAN_API_KEY=your-qianfan-api-key-here
if "%QIANFAN_API_KEY%"=="" (
    echo [WARN] QIANFAN_API_KEY is not set. LLM calls will fail.
    echo         Please set it:  set QIANFAN_API_KEY=your-key
)
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
