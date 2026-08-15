# TeamMind 开发环境搭建

> 让开发者 30 分钟内能 `mvn test` 跑起来。

---

## 前置要求

### 已验证环境（参考）

| 工具 | 版本 | 安装位置 |
|---|---|---|
| JDK | 17.0.2 (Oracle) | `C:\Program Files\Java\jdk-17.0.2` |
| Maven | 3.9.12 | `D:\java\maven\apache-maven-3.9.12` |
| Node.js | 22.20.0 | 自动 |
| npm | 10.9.3 | 自动 |
| OS | Windows 11 | - |

### 需要安装

| 工具 | 最低版本 | 说明 |
|---|---|---|
| JDK | 17+ | 项目统一 Java 17 |
| Maven | 3.8+ | 后端构建 |
| Node | 18+ | 前端构建 |
| Git | 2.30+ | 版本控制 |
| SQLite CLI（可选） | 3.x | 直接看数据库 |

---

## 1. 克隆并初始化

```cmd
git clone https://github.com/yourname/teammind.git
cd teammind
```

---

## 2. 后端搭建

### 2.1 Maven 配置

#### Windows

```cmd
:: 检查 maven
mvn -v

:: 输出应类似：
:: Apache Maven 3.9.12
:: Java version: 17.0.2, vendor: Oracle Corporation
```

#### 如果 mvn 不识别

```cmd
:: 临时添加 PATH
set PATH=D:\java\maven\apache-maven-3.9.12\bin;%PATH%

:: 永久：系统属性 → 环境变量 → 用户变量 PATH → 添加
```

### 2.2 首次编译

```cmd
cd backend
mvn -B compile
```

**预期结果**：
```
[INFO] BUILD SUCCESS
[INFO] Total time:  25.891 s
```

### 2.3 跑测试

```cmd
mvn -B test
```

**预期结果**：114 个测试全过（修复 B1-B8 后）

### 2.4 启动应用

```cmd
mvn -B spring-boot:run
```

**预期结果**：
```
Started TeamMindApplication in 9.2 seconds
Tomcat started on port 8080
```

### 2.5 数据目录

应用启动时会自动创建：

```
%USERPROFILE%\.teammind\
├── data\                  # SQLite 数据库
│   └── teammind.db
├── agents\                # Agent Plugin 临时数据
├── templates\             # （v0.1 保留，v0.2 废弃）
└── logs\                  # 日志
```

**注意**：这是 B8 修复的核心。详见 [w2-schema-migration.md](w2-schema-migration.md)。

---

## 3. 前端搭建

### 3.1 安装依赖

```cmd
cd frontend
npm install
```

**预期结果**：`node_modules/` 创建完毕，无 ERR。

### 3.2 启动开发服务器

```cmd
npm run dev
```

**预期结果**：
```
Local:   http://localhost:3000/
```

### 3.3 跑测试

```cmd
npm run test
```

**预期结果**：115 个测试全过

---

## 4. 一键启动

项目根目录的 `start-all.bat`：

```cmd
cd teammind
start-all.bat
```

**会做**：
1. 检查端口（3000, 8080）
2. 启动 backend（后台）
3. 启动 frontend（后台）
4. 等待服务就绪
5. 自动打开浏览器
6. 显示日志路径

---

## 5. 验证 CLI 探测（开发基础）

后端启动后，测试 CLI 探测：

```cmd
:: 单独运行 demo（不进 spring context）
cd backend
mvn -B compile
java -cp "target/classes;%USERPROFILE%\.m2\repository\org\slf4j\slf4j-api\2.0.13\slf4j-api-2.0.13.jar;%USERPROFILE%\.m2\repository\org\slf4j\slf4j-simple\2.0.13\slf4j-simple-2.0.13.jar" ^
  com.teammind.cli.registry.CLIDiscovery
```

**预期输出**：
```
=================================================
 TeamMind CLI Auto-Discovery
=================================================

  [OK] claude-code  Claude Code   v2.1.215
  [OK] codex        Codex CLI     v0.144.5

  Detected: 2 / 5 CLIs
```

---

## 6. IDE 配置（推荐 IntelliJ IDEA）

### 6.1 项目结构

```
File → Project Structure → Modules
  + backend (Maven auto-import)
  + frontend (npm install)
```

### 6.2 运行配置

```
Run → Edit Configurations
  + Spring Boot
    Main class: com.teammind.TeamMindApplication
    Working dir: backend
    Active profiles: local
```

### 6.3 调试

- 后端：直接在 IntelliJ 启动 `TeamMindApplication.main()`
- 前端：`npm run dev`，Chrome DevTools 调试

---

## 7. 数据库工具

### 7.1 SQLite Browser

下载 [DB Browser for SQLite](https://sqlitebrowser.org/)，打开：

```
%USERPROFILE%\.teammind\data\teammind.db
```

### 7.2 CLI 工具

```cmd
sqlite3 %USERPROFILE%\.teammind\data\teammind.db

sqlite> .tables
sqlite> .schema tasks
sqlite> SELECT * FROM projects;
```

---

## 8. 测试已安装 CLI（开发 Agent Plugin 的前提）

### 8.1 验证 Claude Code

```cmd
claude --version
:: 期望: 2.x.x
```

如果没有：

```cmd
npm install -g @anthropic-ai/claude-code
```

### 8.2 验证 Codex

```cmd
codex --version
:: 期望: 0.x.x
```

如果没有：

```cmd
npm install -g @openai/codex
```

### 8.3 验证 Aider / OpenCode / Gemini

```cmd
aider --version
opencode --version
gemini --version
```

---

## 9. 常见环境问题

### 9.1 mvn 报 sandbox 错误

**问题**：沙盒模式下 `mvn` 写 `~/.m2/repository` 被拒绝。

**解决**：本次会话已授权 `danger-full-access`，可正常运行。

### 9.2 端口被占用

```cmd
:: 查看占用
netstat -ano | findstr :8080

:: 结束占用进程
taskkill /F /PID <pid>
```

或在 `application.yml` 改端口：

```yaml
server:
  port: 8090
```

### 9.3 JDK 版本不对

```cmd
java -version
:: 应输出 17.x

:: 如果版本不对：
set JAVA_HOME=C:\Program Files\Java\jdk-17.0.2
set PATH=%JAVA_HOME%\bin;%PATH%
```

### 9.4 Node 版本过低

```cmd
node -v
:: 应输出 v18+

:: 如果版本过低，用 nvm
nvm install 18
nvm use 18
```

---

## 10. 接下来

- 读 [w2-plugin-runtime.md](w2-plugin-runtime.md) 开始 W2 开发
- 或读 [testing-guide.md](testing-guide.md) 学习测试策略

---

**最后更新**：2026-08-14
**版本**：v0.1