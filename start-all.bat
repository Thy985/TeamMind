@echo off
echo ========================================
echo TeamMind Full Stack Startup
echo ========================================

echo.
echo [INFO] This script will start both backend and frontend
echo [INFO] Backend:  http://localhost:8080
echo [INFO] Frontend: http://localhost:3000
echo.

:: 设置环境变量
set QIANFAN_API_KEY=bce-v3/ALTAKSP-W8fnSI7P0cqEcBxE6SYuM/94fd0c1e5615d09b244576df2d68dfd0739ed5f2
set QIANFAN_BASE_URL=https://qianfan.baidubce.com/v2/coding

:: 启动后端（新窗口）
echo [INFO] Starting backend server...
start "TeamMind Backend" cmd /k "cd /d %~dp0backend && mvn spring-boot:run"

:: 等待后端启动
echo [INFO] Waiting for backend to start...
timeout /t 10 /nobreak > nul

:: 启动前端（新窗口）
echo [INFO] Starting frontend server...
start "TeamMind Frontend" cmd /k "cd /d %~dp0 && npm run dev"

echo.
echo ========================================
echo [SUCCESS] Both servers are starting!
echo ========================================
echo.
echo Backend:  http://localhost:8080
echo Frontend: http://localhost:3000
echo.
echo Press any key to open the frontend in browser...
pause > nul

:: 打开浏览器
start http://localhost:3000

echo.
echo [INFO] Close this window to keep servers running
echo [INFO] Or press Ctrl+C in the server windows to stop them
pause
