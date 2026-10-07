# 架构设计

## 核心决策

### 1. Bot 载体：服务端 Fabric Mod，不是 Mineflayer

| 对比项 | Mineflayer（假客户端） | 本方案（服务端 Mod） |
|---|---|---|
| 版本适配 | 等社区更新 protocol，26.2 暂无 | 直接跑在 26.2 上，零等待 |
| 数据访问 | 靠协议包反推，信息不全 | 直接调服务端 API，完整准确 |
| 物理/寻路 | 自己实现，易和服务端不同步 | 复用服务端逻辑，天然同步 |
| 性能 | 额外跑一个客户端进程 | 同进程，开销小 |
| 反作弊 | 可能被判定为异常客户端 | 服务端内部实体，无协议层问题 |

**代价**：需要写 Java，且 FakePlayer 的输入模拟要自己处理。

### 2. 通信层：Mod 内置 HTTP，不用 RCON

RCON 只能发命令字符串，无法传递结构化数据（坐标、背包、实体列表都要解析文本）。

Mod 内置 HTTP 服务（Javalin）暴露 JSON-RPC：

```json
{
  "jsonrpc": "2.0",
  "method": "bot.moveTo",
  "params": { "x": 100, "y": 64, "z": -200 },
  "id": 1
}
```

### 3. MCP Server 职责

```
AI ──MCP──> MCP Server ──HTTP JSON-RPC──> Mod ──> MC
     <──         <──              <──          <──
```

MCP Server 做四件事：
1. **协议转换**：MCP tool call ↔ JSON-RPC
2. **安全过滤**：游戏状态回传前剥离可疑 prompt injection 模式
3. **会话管理**：多 AI 客户端、多 Bot 的路由
4. **能力编排**：把底层原子操作组合成高层工具（如 `mine_block` = 寻路 + 挖掘 + 拾取）

## 模块划分

### plugin/（Fabric Mod）

| 包 | 职责 |
|---|---|
| `bot` | FakePlayer 实体管理、生成/销毁 |
| `net` | HTTP 服务、JSON-RPC 路由、Token 鉴权 |
| `action` | 原子动作执行器（移动、挖掘、放置、使用、攻击） |
| `sense` | 状态采集（位置、血量、背包、周围方块/实体） |
| `task` | 任务队列与调度（长任务异步化） |

### mcp/（Node.js）

| 目录 | 职责 |
|---|---|
| `src/tools` | MCP 工具定义（每个 tool 对应 plugin 的 1..n 个 RPC） |
| `src/rpc` | JSON-RPC 客户端、连接池、重试 |
| `src/filter` | 安全过滤层 |
| `src/session` | 会话与 Bot 绑定 |

### protocol/

JSON Schema 定义所有 RPC 方法与事件，Java 和 TS 双向生成绑定，避免手写不同步。

## 数据流示例：AI 挖一块石头

```
1. AI: tool_call "mine_block" { target: "stone" }
2. MCP Server:
   - 校验参数
   - RPC: sense.getNearbyBlocks { type: "stone", radius: 16 }
3. Mod:
   - 返回 [{x:101,y:64,z:-200}, ...]
4. MCP Server:
   - 选最近的一块
   - RPC: action.moveTo { x:101, y:64, z:-200 }
   - RPC: action.lookAt { ... }
   - RPC: action.attack { entity: null, block: {x,y,z} }
   - RPC: action.collectDrops
5. Mod: 逐步执行，每步返回结果
6. MCP Server: 汇总为自然语言结果回给 AI
```

## 关键设计点

### FakePlayer 实现

Fabric 没有官方 FakePlayer API，两条路：

| 方案 | 说明 |
|---|---|
| A. 复用 `ServerPlayer` | 构造一个真实 Player 实体，不走网络层。最兼容，但开销大 |
| B. 自写轻量实体 | 只实现 AI 需要的能力，不参与完整玩家逻辑。轻量，但要处理更多边界 |

**选 A**。原因：Inventory、Interaction、Physics 全部免费获得，且 Baritone 等现成寻库依赖真实 Player。

### 寻路

接入 **Baritone**（Fabric 版）。它是 Mineflayer 生态外最成熟的 MC 寻路库，支持 1.12+，26.2 需要验证。

若 Baritone 不支持 26.2，退化方案：直线移动 + 简单跳跃避障。

### 安全过滤（重点）

Issue #261 的漏洞必须防。三层防护：

```
Layer 1: 长度截断    游戏文本字段超长直接截断
Layer 2: 模式剥离    移除 "ignore previous instructions" 等已知注入模式
Layer 3: 结构化隔离  玩家可控内容（告示牌、聊天、物品名）单独放 metadata 字段，
                    不混入主描述文本
```

详见 `SECURITY.md`。

## 部署形态

```
D:\MCSpace\
├── mods\
│   └── agi-mc-bot-bridge-1.0.0.jar    ← 我们的 Mod
├── start.bat
└── ...

D:\agi-mc-bot-server\
├── mcp\           ← MCP Server，AI 客户端配置指向这里
└── ...
```

Mod 和 MCP Server 可同机部署，也可分离（Mod 在服务器，MCP Server 在 AI 客户端机器）。
