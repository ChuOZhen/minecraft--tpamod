# TPA Mod — 玩家间传送请求

为 Minecraft 服务器添加玩家之间的传送请求：**/tpa 发起 → 对方同意 → 传送过去**。
服务端专用模组，客户端无需安装。

---

## ⚠️ 来源声明

> 本项目的**初版由旧模型 v3.2 生成**，面向 Minecraft 1.21.1（Yarn 映射），**且从未成功构建过**：
>
> - `build.gradle` 首行混入了无效字符（`，wuxplugins {`），任何 Gradle 版本都无法解析；
> - 它自带的 `README.txt` 声称 Minecraft 1.21.11 / Loader 0.16.10+，
>   而 `gradle.properties` 里写的是 1.21.1 / 0.16.9 —— 版本描述自相矛盾；
> - 另有 2 处会真实出问题的设计缺陷（见下方「升级说明」）。
>
> **当前版本由 Reasonix 依据《经验.md》升级到 Minecraft 26.3**，修掉了上述构建错误与缺陷，
> 并通过 `./gradlew build` 实测验证（产物 `build/libs/tpa-2.0.0.jar`）。

---

## 功能与命令

| 命令 | 作用 | 权限 |
|---|---|---|
| `/tpa <玩家名>` | 请求传送到指定玩家；**支持 Tab 补全在线玩家名** | 所有玩家 |
| `/tpaccept` | 接受发给你的请求，请求者立即传送过来 | 所有玩家 |
| `/tpdeny` | 拒绝发给你的请求 | 所有玩家 |
| `/tpacancel` | 撤回自己发出的请求 | 所有玩家 |

规则：

- 请求 **60 秒**后自动过期，双方都会收到提示；
- 同一时间每个玩家只能有一个待处理的请求；
- 不能传送到自己，目标必须在线；
- **跨维度传送正确落点**（使用目标所在世界作为传送目标）；
- 所有玩家可见文本都走翻译键，内置 **简体中文 / 英文**。

---

## 环境要求

| 组件 | 版本 |
|---|---|
| Minecraft | **26.3** |
| Fabric Loader | **0.19.5+** |
| Fabric API | 需要（声明了 `fabric-api-base`、`fabric-command-api-v2`、`fabric-lifecycle-events-v1`） |
| Java | **25+** |
| 安装位置 | **仅服务端**（`environment: "server"`，客户端装了也不会加载） |

---

## 构建与安装

```bash
./gradlew build --console=plain     # 产物：build/libs/tpa-2.0.0.jar
```

安装：把 `tpa-2.0.0.jar` 放进服务器的 `mods/` 目录，重启服务器即可（客户端不需要装）。

本仓库自带 `build.bat`（Windows 一键构建）。

---

## 升级说明：1.21.1 → 26.3

### 构建工具链

| 项 | 初版（v3.2） | 现在 |
|---|---|---|
| Minecraft | 1.21.1 | **26.3** |
| 映射 | Yarn `1.21.1+build.3` | **恒等映射**（26.3 无可用 Yarn，见下） |
| Loader | 0.16.9 | **0.19.5** |
| Loom | `1.9-SNAPSHOT` | **1.18.2**（要求 Gradle ≥ 9.7.0） |
| Gradle | — | **9.7.1**（wrapper 已配好） |
| Java | 21 | **25** |
| Fabric API | maven 依赖 | 本地模块 jar（避免 classTweaker 命名空间被 remap） |

**关于映射**：26.3 没有可用的 Yarn 映射（`meta.fabricmc.net` 对 26.3 返回空数组，
官方版本清单也没有 `client_mappings`），因此本项目使用**恒等映射**
（`gradle/identity-mappings.jar`），源码按 **Mojang 名（命名版）** 书写。

### API 名称变更（本次实际改动）

| 初版写法（Yarn） | 26.3 实际（Mojang 名） |
|---|---|
| `net.minecraft.server.command.ServerCommandSource` | `net.minecraft.commands.CommandSourceStack` |
| `net.minecraft.server.command.CommandManager` | `net.minecraft.commands.Commands` |
| `net.minecraft.server.network.ServerPlayerEntity` | `net.minecraft.server.level.ServerPlayer` |
| `net.minecraft.text.Text` | `net.minecraft.network.chat.Component` |
| `CommandRegistryAccess` | `CommandBuildContext`（本项目未直接使用，回调参数省略类型） |
| `getPlayerManager().getPlayerList()` | `getPlayerList().getPlayers()` |
| `getPlayerManager().getPlayer(String)` | `getPlayerList().getPlayerByName(String)` |
| `source.sendFeedback(Supplier<Text>, boolean)` | `source.sendSuccess(Supplier<Component>, boolean)` |
| `source.sendError(Text)` | `source.sendFailure(Component)` |
| `player.sendMessage(Text, false)` | `player.sendSystemMessage(Component)` |
| `player.getUuid()` | `player.getUUID()` |
| `player.getGameProfile().getName()` | `player.getScoreboardName()`（26.3 的 `GameProfile` 已无 `getName()`） |
| `teleport(x, y, z, boolean)` | `teleportTo(ServerLevel, x, y, z, Set<Relative>, yRot, xRot, boolean)` |
| 硬编码 `§` 颜色码 | 翻译键 + `assets/tpa/lang/*.json` |

### 修复的缺陷

| # | 初版问题 | 现在的做法 |
|---|---|---|
| 1 | **并发缺陷**：每来一个请求就 `new Thread(...).sleep(60000)`，然后从**后台线程**修改主线程正在读写的 `HashMap` —— 数据竞争，可能丢失请求或抛 `ConcurrentModificationException` | 改为**服务端主线程 tick 驱动**：`ServerTickEvents.END_SERVER_TICK` 每刻检查时间戳（`TpaRequests.tick`），无额外线程 |
| 2 | **跨维度传送错误**：只传坐标 `teleport(x, y, z, true)`，两人处于不同维度时会传到错误位置 | 使用带 `ServerLevel` 与朝向的 `teleportTo(...)`，显式指定目标所在世界 |
| 3 | 请求过期时**不给任何提示**（代码里只留了 TODO 注释） | 过期时通知请求者与目标双方 |
| 4 | `fabric.mod.json` 的 `depends` 写了聚合的 `"fabric-api": "*"`，在 Fabric API 以模块形式提供时会报「需要 fabric-api 但没有安装」 | 改为具体模块：`fabric-api-base`、`fabric-command-api-v2`、`fabric-lifecycle-events-v1` |
| 5 | 引用不存在的 `assets/tpa/icon.png`，`suggests: {"flamingo": "*"}` 是模板残渣 | 一并移除 |
| 6 | 缺少 `gradlew`（Unix 脚本）与 `LICENSE`（`jar` 任务会引用） | 已补齐 |

---

## 目录结构

```
.
├── build.gradle                 构建脚本（Loom 1.18.2 / Java 25 / 恒等映射）
├── gradle.properties            项目标识与工具链版本号
├── settings.gradle
├── gradlew / gradlew.bat        Gradle 9.7.1 wrapper
├── gradle/
│   ├── wrapper/
│   └── identity-mappings.jar    ★ 26.3 恒等映射
├── libs/fabric-api-modules/     ★ Fabric API 模块 jar（编译依赖）
├── build.bat                    Windows 一键构建
└── src/main/
    ├── java/com/example/tpamod/
    │   ├── TpaMod.java          服务端入口（注册命令 + tick）
    │   ├── TpaCommand.java      四个命令的实现
    │   └── TpaRequests.java     请求表（tick 驱动的过期检查）
    └── resources/
        ├── fabric.mod.json
        └── assets/tpa/lang/     中英文翻译
```

---

## 已知限制

- 请求**不持久化**：服务器重启会丢弃所有待处理请求（对传送请求而言通常可接受）。
- 目标玩家在请求期间离线时，接受/拒绝会提示「请求者已离线」，请求随即被清理。
- 传送不检查目标位置是否安全（与 `/tp` 行为一致），也不做传送冷却 ——
  如需防盗刷请在服务端另行加插件/权限限制。
- 传送时会保留请求者的朝向参数为**目标玩家的朝向**（便于面对面），而非其原朝向。

---

## 许可

MIT，见 [LICENSE](LICENSE)。
