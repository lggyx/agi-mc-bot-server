# agi-mc-bot-server

自研 Minecraft AI Bot 服务端 —— 让 AI 通过 MCP 协议真正"进入"游戏世界操作角色。

## 定位

| 维度 | 本项目的选择 |
|---|---|
| 目标版本 | **Minecraft 26.2**（Fabric Loader 0.19.5） |
| 架构 | **服务端 Fabric Mod 承载 Bot**，非 Mineflayer 客户端方案 |
| 通信 | Mod 内置 HTTP/WS 服务，MCP Server 作为协议转换层 |
| 首要能力 | AI 玩家操作（移动、挖掘、放置、交互、背包） |
| 次要能力 | 服务端监控与数据整合（TPS、玩家、日志） |

## 为什么不照搬 Mineflayer 方案

调研 `yuniko-software/minecraft-mcp-server`（773 star）后确认：

1. **版本断层**：main 分支最高支持到 26.1.2，PR #274 提交 5 天无人合并
2. **维护停滞**：连 Dependabot 依赖更新都无人处理，19 个 issue 零回应
3. **架构受限**：Mineflayer 是"假客户端"，无法访问服务端 API（实体、背包、世界数据都要靠协议猜）
4. **安全漏洞**：Issue #261 指出未过滤的游戏状态会直接进 LLM 上下文，可被恶意玩家 prompt injection

## 架构

```
┌─────────────────┐
│   AI 客户端     │  Claude Desktop / VS Code Copilot
└────────┬────────┘
         │ MCP (stdio / SSE)
┌────────▼────────┐
│  MCP Server     │  Node.js + @modelcontextprotocol/sdk
│  (mcp/)         │  协议转换 + 安全过滤 + 会话管理
└────────┬────────┘
         │ HTTP JSON-RPC + Token 鉴权
┌────────▼────────┐
│  Bot Bridge Mod │  Fabric Mod (Java 25)
│  (plugin/)      │  FakePlayer 实体 + 控制接口 + 状态上报
└────────┬────────┘
         │
┌────────▼────────┐
│  MC 26.2 服务端 │  D:\MCSpace
└─────────────────┘
```

## 目录结构

```
agi-mc-bot-server/
├── docs/
│   ├── ARCHITECTURE.md      架构详解
│   ├── ROADMAP.md           开发路线
│   └── SECURITY.md          安全设计（prompt injection 防护）
├── plugin/                  Fabric Mod（Java）
│   ├── build.gradle
│   └── src/main/java/...
├── mcp/                     MCP Server（Node.js/TypeScript）
│   ├── package.json
│   └── src/
├── protocol/                共享协议定义（JSON Schema）
└── README.md
```

## 快速开始

待开发完成后补充。

## 许可

MIT
