# 开发路线

## Phase 0：环境与骨架（当前）

- [x] 仓库结构
- [x] 架构文档
- [x] 安全设计
- [ ] Fabric Mod 工程搭建（Gradle + Loom）
- [ ] MCP Server 工程搭建（TypeScript）
- [ ] 协议定义（JSON Schema）

**产出**：能编译、能启动、无功能的空壳。

## Phase 1：最小闭环（MVP）

目标：AI 能让 Bot 移动和观察。

| 模块 | 内容 |
|---|---|
| Mod | FakePlayer 生成、HTTP 服务、Token 鉴权 |
| Mod | `sense.getState`（位置、血量、朝向） |
| Mod | `action.moveTo`（直线移动） |
| Mod | `action.lookAt`（视角控制） |
| MCP | JSON-RPC 客户端 |
| MCP | 3 个 tool：`get_state`、`move_to`、`look_at` |
| MCP | 安全过滤 Layer 1 + 3 |

**验收**：在 Claude Desktop 里说"往东走 10 格"，Bot 动。

## Phase 2：世界交互

| 模块 | 内容 |
|---|---|
| Mod | `sense.getNearbyBlocks`（周围方块） |
| Mod | `sense.getNearbyEntities`（周围实体） |
| Mod | `sense.getInventory` |
| Mod | `action.mineBlock` |
| Mod | `action.placeBlock` |
| Mod | `action.useItem` |
| Mod | `action.attack` |
| MCP | 对应高层工具 + 安全过滤 Layer 2 |

**验收**：AI 能挖一块石头并放进背包。

## Phase 3：寻路与任务

| 模块 | 内容 |
|---|---|
| Mod | 接入 Baritone（或自研简易寻路） |
| Mod | 任务队列（长任务异步，支持取消） |
| Mod | `task.goto`（带避障的移动） |
| MCP | 任务状态查询、取消 |

**验收**：AI 说"去那个村庄"，Bot 自动绕障碍过去。

## Phase 4：服务端监控（次要需求）

| 模块 | 内容 |
|---|---|
| Mod | `server.getStatus`（TPS、MSPT、内存、在线玩家） |
| Mod | `server.getLogs`（最近日志，过滤后） |
| Mod | 事件推送（WS）：玩家进出、死亡、异常 |
| MCP | 监控工具 + 事件订阅 |

**验收**：AI 能回答"现在服务器几个人、TPS 多少"。

## Phase 5：打磨

- [ ] 多 Bot 支持
- [ ] 配置热重载
- [ ] 完整审计日志
- [ ] 单元测试 + 集成测试
- [ ] 文档与示例

## 技术风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| Baritone 不支持 26.2 | Phase 3 阻塞 | 自研简易寻路退化 |
| FakePlayer 物理异常 | Bot 行为诡异 | 充分测试，必要时降级为"传送式"移动 |
| Fabric API 26.2 变更大 | 开发受阻 | 紧跟 Fabric 版本，API 封装隔离 |
| MCP SDK 演进 | 协议不兼容 | 锁定 SDK 版本，关注 changelog |

## 版本对应

| 组件 | 版本 |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.2 |
| Java | 25 |
| Gradle | 8.x |
| Node.js | 22+ |
| @modelcontextprotocol/sdk | 锁定最新稳定版 |
