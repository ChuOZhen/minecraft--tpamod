# TPA Mod — 玩家间传送请求

为 Minecraft 服务器添加玩家之间的传送请求：**/tpa 发起 → 对方同意 → 传送过去**。
服务端专用模组，客户端无需安装。

---

## ⚠️ 来源声明

> 本项目的**初版由旧模型 v3.2 生成**，面向 Minecraft 1.21.1（Yarn 映射），**仓库源码无法被构建**：
>
> - `build.gradle` 首行混入了无效字符（`，wuxplugins {`），任何 Gradle 版本都无法解析它；
>   仓库中 `v1.0.0` 那份旧 jar（2026-04-15）来自更早或其它状态，**无法由本仓库当前源码复现**；
> - 它自带的 `README.txt` 声称 Minecraft 1.21.11 / Loader 0.16.10+，
>   而 `gradle.properties` 里写的是 1.21.1 / 0.16.9 —— 版本描述自相矛盾；
> - 另有 2 处会真实出问题的设计缺陷（见下方「升级说明」）。
>
> **2.0.0 由 Reasonix 依据《经验.md》升级到 Minecraft 26.3**，修掉了上述构建错误与缺陷，
> 并通过 `./gradlew build` 实测验证（产物 `build/libs/tpa-2.1.0.jar`）。
>
> **2.1.0 加入聊天栏可点击按钮**（【同意】绿 /【拒绝】红 /【取消】），并修掉了
> 「多人同时请求同一目标时会点错人」与「对方离线后请求空挂 60 秒」两处缺陷（见下方「2.1.0 变更」）。

---

## 功能与命令

| 命令 | 作用 | 权限 |
|---|---|---|
| `/tpa <玩家名>` | 请求传送到指定玩家；**支持 Tab 补全在线玩家名** | 所有玩家 |
| `/tpaccept [玩家名]` | 接受发给你的请求，请求者立即传送过来；也可直接点聊天栏的**【同意】**。不填玩家名时，仅当只有一个待处理请求时可用 | 所有玩家 |
| `/tpdeny [玩家名]` | 拒绝发给你的请求；也可直接点**【拒绝】** | 所有玩家 |
| `/tpacancel` | 撤回自己发出的请求；也可直接点**【取消】** | 所有玩家 |

规则：

- 请求 **60 秒**后自动过期，双方都会收到提示；**任一方离线后请求立即失效**并通知另一方；
- 同一时间每个玩家只能有一个待处理的请求（但**可以同时收到多个人的请求**，用名字区分）；
- 不能传送到自己，目标必须在线；
- **跨维度传送正确落点**（使用目标所在世界作为传送目标）；
- 所有玩家可见文本都走翻译键，内置 **简体中文 / 英文**。

### 聊天栏按钮（2.1.0）

`/tpa` 发出后，**双方**都会在聊天栏收到一条带按钮的消息 —— 服务端用原版
`ClickEvent.RunCommand` 构造，**客户端无需安装模组**：

| 谁看到 | 文案 | 按钮 |
|---|---|---|
| 目标玩家 | `Alice 请求传送到你身边` | **【同意】**（绿色）、**【拒绝】**（红色） |
| 请求者 | `已向 Bob 发送传送请求（60 秒内有效）` | **【取消】**（金色） |

- 按钮点击后执行的命令与手输完全一致（`/tpaccept Alice` / `/tpdeny Alice` / `/tpacancel`），
  手输命令依然可用；
- 鼠标悬停在按钮上会显示说明（如「点击同意：允许 Alice 传送到你身边」）；
- **按钮带上了请求者名字**：A、C 同时请求 B 时，B 点哪一条就处理哪一条，
  `/tpaccept` 的 Tab 补全也只会列出「正在请求我的人」；
- 请求失效（被接受 / 被拒绝 / 已取消 / 已过期）后再点旧按钮，只会提示「没有待处理的传送请求」。

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
./gradlew build --console=plain     # 产物：build/libs/tpa-2.1.0.jar
```

安装：把 `tpa-2.1.0.jar` 放进服务器的 `mods/` 目录，重启服务器即可（客户端不需要装）。

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

## 2.1.0 变更：聊天栏按钮

### 新增

| 项 | 说明 |
|---|---|
| 可点击按钮 | 目标玩家收到绿色**【同意】**/ 红色**【拒绝】**，请求者收到金色**【取消】**，均带悬停说明 |
| 实现方式 | 原版 `ClickEvent.RunCommand`（`Style#withClickEvent` + `HoverEvent.ShowText`），**纯服务端能力**，客户端无需安装模组 |
| `/tpaccept [玩家名]`、`/tpdeny [玩家名]` | 新增可选参数；Tab 补全只列出「正在请求我的人」，不再混入无关玩家 |
| 翻译键 | 新增 `tpa.button.*`、`tpa.hover.*`、`tpa.message.target_offline`、`tpa.message.requester_left` 等，中英同步 |

### 修复

| # | 2.0.0 的问题 | 现在的做法 |
|---|---|---|
| 7 | **多人请求同一目标时会点错人**：请求表以请求者为键，而 `/tpaccept` 只按「目标」查找并返回遍历到的第一条 —— A、C 同时请求 B 时，B 点【同意】可能传送的是另一个人 | 查询改为返回全部匹配项（`findIncoming` 返回列表 + 新增 `find(requester, target)` 精确查找）；按钮与 `/tpaccept`、`/tpdeny` 一律带上请求者名字，点谁就是谁；未指定名字而确有多个请求时明确提示「请指定玩家名」，而不是随便挑一个 |
| 8 | **对方离线后请求空挂 60 秒**：目标玩家离线后请求仍留在表里，请求者要等到超时才知道 | `TpaRequests.tick` 每刻检查双方在线状态，任一方离线即清理并提示仍在线的另一方 |
| 9 | **`download_wrapper.bat` 是个陷阱**：它下载 **Gradle 8.5.0** 的 wrapper jar 覆盖现有 wrapper，而 Loom 1.18.2 要求 Gradle ≥ 9.7.0 —— 一旦运行就把项目从「可构建」变成「无法构建」 | 删除该脚本（仓库已自带配置好的 9.7.1 wrapper：`gradlew` + `gradle/wrapper/`），`build.bat` 里过期的 `tpa-1.0.0.jar` 一并改正 |

> ⚠️ 按钮「看起来能点」不等于「点了有效」：服务端发出的消息走
> `ComponentSerialization.TRUSTED_STREAM_CODEC`，而点击事件另受
> `ClickEvent.Action#isAllowedFromServer` 过滤（例如 `open_file` 就不允许来自服务端）。
> 因此 2.1.0 的按钮经**序列化实测**确认 `click_event` 会原样到达客户端（见文末「验证记录」）。

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
    │   ├── TpaCommand.java      四个命令 + 聊天栏按钮（同意/拒绝/取消）
    │   └── TpaRequests.java     请求表（多对一查询、过期与离线清理）
    └── resources/
        ├── fabric.mod.json
        └── assets/tpa/lang/     中英文翻译
```

---

## 已知限制

- 请求**不持久化**：服务器重启会丢弃所有待处理请求（对传送请求而言通常可接受）。
- **任一方在请求期间离线**时，请求会在下一 tick 立即失效并通知另一方（不必等到 60 秒超时）；
  若目标玩家恰好离线，请求者会收到「目标玩家已离线，你的传送请求已失效」。
- 传送不检查目标位置是否安全（与 `/tp` 行为一致），也不做传送冷却 ——
  如需防盗刷请在服务端另行加插件/权限限制。
- 传送时会保留请求者的朝向参数为**目标玩家的朝向**（便于面对面），而非其原朝向。

---

## 验证记录（2.1.0，本机实测）

| 验证项 | 方式 | 结果 |
|---|---|---|
| 构建 | `./gradlew build --console=plain` | `BUILD SUCCESSFUL`，产物 `build/libs/tpa-2.1.0.jar` |
| 模组加载 | `./gradlew runServer`（专用服务端，Minecraft 26.3 / Loader 0.19.5） | `Loading 42 mods: - tpa 2.1.0`、`[tpa] 初始化完成`、服务端 `Done`，无异常 |
| 命令注册与执行 | 通过 RCON 向运行中的服务端注入命令 | `help tpa` → `/tpa [<player>]`；`help tpaccept` → `/tpaccept [<player>]`；`tpa` → `Usage: /tpa <player>`；`tpa Alice` / `tpaccept` / `tpaccept Alice` / `tpdeny` / `tpacancel` → `Only players can use this command`，即命令已注册且执行到了模组代码 |
| 按钮组件 | 反射调用生产代码的按钮构造，再做 26.3 组件序列化 | 输出含 `{"translate":"tpa.button.accept","color":"green","bold":true,"click_event":{"command":"/tpaccept Alice","action":"run_command"}}`，`click_event` 未被过滤 |
| 请求表多对一 | 直接调用 `TpaRequests` 的公开方法 | 两人同时请求同一目标时可查到 2 条；按名字精确移除其中一条后，另一条仍在 |
| 翻译键 | 源码引用的键 ↔ `zh_cn.json` / `en_us.json` | 27 个键三方完全一致，无缺失、无死键 |

**未覆盖**：两名真实玩家之间的点击交互需要图形客户端与有效账号，无法在本机自动化验证。
按钮生效的服务端环节（命令已注册 + 消息确实带 `click_event`）均由上表实测覆盖。

---

## 许可

MIT，见 [LICENSE](LICENSE)。
