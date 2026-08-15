# Playwright Vue Component Tester — DSH 集成指南

## ✅ 集成完成

插件已成功集成到 DSH，Chromium 浏览器已安装，测试全部通过。

---

## 文件清单

| 文件 | 说明 |
|------|------|
| `plugins/playwright-vue-tester.host.cjs` | **Host 插件** — Playwright 自动化 + 500ms 网络延迟模拟 + 动态工具注册 |
| `plugins/playwright-vue-tester.client.js` | Client 插件 — 对话输入区测试面板 + 状态浮窗 |
| `scripts/playwright-test-runner.cjs` | 独立测试运行脚本 |
| `src/components/CounterWidget.vue` | 示例组件：计数器 |
| `src/components/AsyncLoader.vue` | 示例组件：异步加载 |
| `playwright.config.js` | Playwright E2E 配置 |
| `src/components/__tests__/counter-widget.spec.js` | E2E 测试用例 |

**DSH 配置**: `C:\Users\lenovo\.dsh\profiles\web\cordis.patch.yml`

---

## 测试结果

```
✅ CounterWidget:  success=true, totalElapsedMs=2108ms (含500ms延迟)
✅ AsyncLoader:    success=true, totalElapsedMs=2057ms (含500ms延迟)
✅ Headed 模式:    success=true, totalElapsedMs=2089ms (浏览器可见)
```

---

## 使用方法

### 方式一：命令行（推荐）

```bash
# Headless 模式
node scripts/playwright-test-runner.cjs CounterWidget
node scripts/playwright-test-runner.cjs AsyncLoader

# Headed 模式（可见浏览器）
node scripts/playwright-test-runner.cjs CounterWidget --headed

# 自定义延迟
node scripts/playwright-test-runner.cjs CounterWidget --delay=1000
```

### 方式二：pnpm 脚本

```bash
pnpm test:playwright CounterWidget
pnpm test:playwright:headed CounterWidget
```

### 方式三：DSH 动态工具

重启 DSH 后，模型可直接调用 `playVuetest` 工具：

```
playVuetest(component: "CounterWidget", delayMs: 500, headless: true)
```

---

## 网络延迟模拟原理

```javascript
// Playwright 拦截所有网络请求，统一追加延迟
await page.route('**/*', async route => {
  await new Promise(r => setTimeout(r, 500)); // 500ms 延迟
  route.continue();
});
```

---

## 当前状态

| 项目 | 状态 |
|------|------|
| Playwright 安装 | ✅ 已完成 |
| Chromium 浏览器 | ✅ 已安装 |
| Host 插件 | ✅ 已加载 |
| cordis.patch.yml | ✅ 已配置 |
| 网络延迟模拟 | ✅ 500ms 正常工作 |
| Vue 组件渲染 | ✅ 真实 Chromium 渲染 |
