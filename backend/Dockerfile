# TeamMind Backend
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# 安装必要工具
RUN apk add --no-cache bash

# 复制构建产物
COPY target/*.jar app.jar

# 创建数据目录
RUN mkdir -p /data/teammind

# 设置环境变量
ENV TEAMMIND_DATA_PATH=/data/teammind
ENV SPRING_PROFILES_ACTIVE=prod

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
