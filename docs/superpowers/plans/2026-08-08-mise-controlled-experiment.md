# mise 受控实验 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 安装 mise，并在 TeamMind、campus-order 前端和 AI Agent-CLI 中完成不接管现有运行时的最小受控实验。

**Architecture:** 使用现有 Scoop 安装 mise；通过项目级 `mise.toml` 描述当前主版本约束和只读 `info` 任务。TeamMind 与 campus-order 只在前端边界配置 Node，AI Agent-CLI 配置 Bun；不启用 shell activation，不执行 `mise install`，不修改现有依赖或全局环境。

**Tech Stack:** Windows 11、Scoop、mise、Node.js/npm、Bun、TOML、Vue/Vite、React/Vite、Spring Boot。

## Global Constraints

- 只新增三个 `mise.toml`，以及实验设计和记录文档。
- 不执行 `mise install`，不启用 shell activation，不执行 `mise trust -a`。
- 不修改 `package.json`、lockfile、`node_modules`、Java/Maven/Gradle/Android 配置、数据库、Docker 或全局 PATH。
- 版本约束必须来自安装前的实际 `node --version` / `bun --version`，只写主版本。
- `info` 任务必须是只读、Windows 兼容，并且不启动业务服务。
- 不删除或卸载现有 Node、Bun、npm、pnpm、Java、Maven 或 Scoop 包。
- 回滚只删除本实验新增的三个 `mise.toml`；设计和记录文档保留审计证据。

---

### Task 1: Install mise without changing project runtimes

**Files:**
- Modify: Scoop-managed installation state outside the repositories; no project file changes.

**Interfaces:**
- Produces: `mise` executable available to the current shell, plus a version and doctor report.

- [ ] **Step 1: Capture the pre-install runtime baseline**

Run from Bash without modifying files:

```bash
python -c "import shutil, subprocess; commands=['node','npm','bun','mise']; print({c: shutil.which(c) for c in commands});\nfor c in commands:\n p=shutil.which(c)\n if p:\n  try: print(c, subprocess.run([c,'--version'],capture_output=True,text=True).stdout.strip() or subprocess.run([c,'--version'],capture_output=True,text=True).stderr.strip())\n  except Exception as e: print(c, type(e).__name__, str(e))"
```

Record the existing `node` and `bun` major versions. If `bun` is not installed, do not install it in this task; the AI Agent-CLI experiment will use a non-installing diagnostic configuration.

- [ ] **Step 2: Install mise through Scoop**

Run:

```bash
scoop install mise
```

Expected: Scoop reports a successful installation or that mise is already installed. Do not run `scoop update`, `scoop cleanup`, or any unrelated package operation.

- [ ] **Step 3: Verify the installation**

Run:

```bash
mise --version
mise doctor
```

Expected: `mise --version` returns a version; `mise doctor` completes. A doctor warning about shell activation is acceptable because activation is intentionally out of scope; an executable or installation error is not.

---

### Task 2: Add the TeamMind mise experiment configuration

**Files:**
- Create: `D:/Projects/Active/TeamMind/mise.toml`

**Interfaces:**
- Consumes: The Node major version captured in Task 1.
- Produces: A project-local `mise.toml` with `[tools] node = "<captured-major>"` and a read-only `mise run info` task.

- [ ] **Step 1: Record the repository baseline before writing**

Run:

```bash
git -C 'D:/Projects/Active/TeamMind' status --short
```

Do not overwrite or reset existing changes. Confirm `mise.toml` is not already present before creating it.

- [ ] **Step 2: Write the minimal configuration**

Use the captured Node major in this exact structure; replace only `<NODE_MAJOR>` with the observed number:

```toml
[tools]
node = "<NODE_MAJOR>"

[tasks.info]
description = "Report the project runtime without changing files"
run = "node -e \"console.log(JSON.stringify({cwd: process.cwd(), node: process.version, npm: require('child_process').execFileSync('npm', ['--version'], {encoding: 'utf8'}).trim()}, null, 2))\""
```

The task only reads process and version information. It must not run `npm install`, build, test, or start the Spring Boot backend.

- [ ] **Step 3: Validate configuration and run the diagnostic**

From the TeamMind directory, run:

```bash
cd 'D:/Projects/Active/TeamMind'
mise config
mise run info
```

Expected: mise reads the project configuration; `mise run info` prints JSON containing `cwd`, a Node version, and an npm version. If mise asks to trust the newly created local file, trust only this exact file with the interactive confirmation; do not use `mise trust -a`.

---

### Task 3: Add the campus-order frontend mise experiment configuration

**Files:**
- Create: `D:/Projects/Active/campus-order/front/app/mise.toml`

**Interfaces:**
- Consumes: The Node major version captured in Task 1.
- Produces: A frontend-local `mise.toml` with `[tools] node = "<captured-major>"` and a read-only `mise run info` task.

- [ ] **Step 1: Record the frontend baseline before writing**

Run:

```bash
git -C 'D:/Projects/Active/campus-order' status --short
```

Confirm no `front/app/mise.toml` exists. Do not inspect or modify `backend/.env`, database settings, Redis settings, or Docker state as part of this experiment.

- [ ] **Step 2: Write the frontend-only configuration**

Use the captured Node major in this exact structure; replace only `<NODE_MAJOR>`:

```toml
[tools]
node = "<NODE_MAJOR>"

[tasks.info]
description = "Report the frontend runtime without changing files"
run = "node -e \"console.log(JSON.stringify({cwd: process.cwd(), node: process.version, npm: require('child_process').execFileSync('npm', ['--version'], {encoding: 'utf8'}).trim()}, null, 2))\""
```

The file must remain under `front/app` so it does not impose a Node configuration on the Spring Boot repository root.

- [ ] **Step 3: Validate configuration and run the diagnostic**

Run:

```bash
cd 'D:/Projects/Active/campus-order/front/app'
mise config
mise run info
```

Expected: configuration discovery succeeds and the diagnostic prints the frontend path, Node version, and npm version. Do not run `npm install`, Vite, Playwright, Maven, Docker Compose, MySQL, or Redis.

---

### Task 4: Add the AI Agent-CLI mise experiment configuration

**Files:**
- Create: `D:/Projects/Active/AI Agent-CLI/mise.toml`

**Interfaces:**
- Consumes: The observed Bun major from Task 1; if Bun is absent, use a non-installing diagnostic configuration without `[tools]` rather than inventing a version.
- Produces: A project-local `mise.toml` with a Bun constraint only when Bun is already present, plus a read-only `mise run info` task.

- [ ] **Step 1: Check the existing Bun state**

Run:

```bash
cd 'D:/Projects/Active/AI Agent-CLI'
bun --version
where.exe bun
```

If both commands succeed, capture the Bun major. If either fails, do not install Bun and omit the `[tools]` section for this experiment.

- [ ] **Step 2: Write the minimal configuration**

When Bun is present, use:

```toml
[tools]
bun = "<BUN_MAJOR>"

[tasks.info]
description = "Report the CLI runtime without changing files"
run = "bun -e \"console.log(JSON.stringify({cwd: process.cwd(), bun: Bun.version, node: process.version}, null, 2))\""
```

When Bun is absent, use only:

```toml
[tasks.info]
description = "Report the available CLI runtime without changing files"
run = "node -e \"console.log(JSON.stringify({cwd: process.cwd(), node: process.version}, null, 2))\""
```

Do not modify the Bun lockfile, package manifest, dependency directory, or CLI source.

- [ ] **Step 3: Validate configuration and run the diagnostic**

Run:

```bash
cd 'D:/Projects/Active/AI Agent-CLI'
mise config
mise run info
```

Expected: configuration discovery succeeds and `mise run info` returns JSON. Do not run `bun install`, build, tests, `bun link`, or the CLI server.

---

### Task 5: Run the cross-project regression and scope checks

**Files:**
- Read-only checks across the three repositories.
- Verify: `D:/Projects/Active/TeamMind/mise.toml`, `D:/Projects/Active/campus-order/front/app/mise.toml`, `D:/Projects/Active/AI Agent-CLI/mise.toml`.

**Interfaces:**
- Consumes: The three configurations and diagnostic results from Tasks 2-4.
- Produces: Verification evidence showing only intended files changed.

- [ ] **Step 1: Check each repository diff**

Run:

```bash
git -C 'D:/Projects/Active/TeamMind' status --short

git -C 'D:/Projects/Active/campus-order' status --short

git -C 'D:/Projects/Active/AI Agent-CLI' status --short
```

Expected: only the intended `mise.toml` files appear, plus any pre-existing changes that must be distinguished from this experiment. The design document under TeamMind may also appear if that repository tracks it. No `package.json`, lockfile, source, or dependency directory may be newly changed by this experiment.

- [ ] **Step 2: Verify manifests and lockfiles are unchanged**

Run a hash check before and after is not available if the pre-install hashes were not recorded; therefore compare Git status and inspect the diff directly:

```bash
git -C 'D:/Projects/Active/TeamMind' diff -- package.json package-lock.json pnpm-lock.yaml yarn.lock

git -C 'D:/Projects/Active/campus-order' diff -- front/app/package.json front/app/package-lock.json front/app/pnpm-lock.yaml front/app/yarn.lock

git -C 'D:/Projects/Active/AI Agent-CLI' diff -- package.json package-lock.json pnpm-lock.yaml bun.lockb bun.lock
```

Expected: no diff caused by this experiment.

- [ ] **Step 3: Record final evidence**

Create or update a short experiment record at:

```text
D:/Projects/Active/TeamMind/docs/superpowers/records/2026-08-08-mise-controlled-experiment.md
```

Record: mise version, doctor result, observed Node/Bun versions, the three config paths, each `mise run info` result, any trust prompt outcome, the exact files shown by Git status, and any warnings. Do not include API keys, tokens, or full environment dumps.

- [ ] **Step 4: Confirm rollback readiness**

Verify the only experiment-owned project files are the three `mise.toml` files. If rollback is requested, remove only those files and rerun the Git status checks; do not remove mise's global installation or unrelated user data without a separate confirmation.

---

## Execution order

Run Tasks 1 through 5 sequentially. Stop before any step that would require `mise install`, shell activation, global PATH changes, package installation, service startup, or modification outside the listed files. Report the blocker and ask for a new scope approval instead of expanding the experiment.
