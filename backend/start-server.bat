@echo off
:: 请先设置环境变量 QIANFAN_API_KEY（勿在脚本中硬编码真实 Key）
:: set QIANFAN_API_KEY=your-qianfan-api-key-here
if "%QIANFAN_API_KEY%"=="" (
    echo [WARN] QIANFAN_API_KEY is not set. LLM calls will fail.
)
java -jar target/teammind-backend-0.1.0.jar
