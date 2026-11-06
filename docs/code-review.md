# 当前仓库代码审查

> 2026-11-01 后续处理：以下三项问题均已核实并修复，详见[修复记录及验证](code-review-fixes.md)。本文保留修复前的审查证据；下方源码位置和行为描述对应审查当时的版本。

审查对象：当前工作区完整实现，包括未提交的修改和新增的 M3-A 文件。基础提交为 `893b82ca7ee8e9d279713077d262313e4b996642`，本报告不是仅针对该提交的 diff 审查。

本次只新增此报告，没有修改现有源码、测试或配置，没有提交代码，也没有启动 Minecraft、操作游戏存档或生成地形。

## 主要发现

共发现 **3 项需要修正的问题：1 项 P1、2 项 P2**。P1 表示建议优先修复的保护缺口；P2 表示应安排修复的功能或验收问题。证据强度分别注明，隔离程序验证不等于真实客户端验收。

### 1. [P1] 训练前排队的延迟挖掘可以绕过事务期间的方块保护

**位置：** [SessionManager.java:98](../src/main/java/dev/hotbarswaptrainer/session/SessionManager.java#L98)、[ServerPlayNetworkHandlerMixin.java:45](../src/main/java/dev/hotbarswaptrainer/mixin/ServerPlayNetworkHandlerMixin.java#L45)。

当前挖掘保护只拦截进入 `onPlayerAction` 的新网络包。START 检查了地面、载具、物品使用和界面状态，却没有排除或清除 `ServerPlayerInteractionManager` 中已有的延迟挖掘状态，也没有在实际破坏方块的入口设置保护。

缓存中的 Minecraft 1.16.1 原版源码确认了另一条执行路径：

```text
训练开始前收到 STOP_DESTROY_BLOCK，服务端挖掘进度不足 0.7
  → failedToMine = true，保存 failedMiningPos / failedStartMiningTime
  → 玩家开始训练，背包备份和替换完成
  → ServerPlayerInteractionManager.update()
  → continueMining() 的进度达到 1.0
  → tryBreakBlock()
  → world.removeBlock()，并可能对当前训练物品执行 postMine()
```

后半段不经过 `ServerPlayNetworkHandler.onPlayerAction`，所以已有 Mixin 不会拦住它。该延迟状态用于处理客户端与服务端挖掘进度不同步，并不要求训练期间再发送一个挖掘包。若目标恰好是站立支撑方块，Stationary 后续检测并停止训练也只能恢复背包，不能撤销方块破坏。

**影响：** 事务保护有效期间仍可能修改地形；训练工具还可能被扣耐久，随后使题目因 NBT 不守恒而失效。当前测试世界门禁限制了影响范围，但不补上这条保护路径。

**证据：** 已阅读实际缓存的原版 `ServerPlayerInteractionManager.java`，并在临时 Java 程序中使用原版 `update()`、内存替身 world/player 和预置的延迟挖掘状态复现回调。玩家的 `InventoryService.isProtected()` 返回 `true`，没有发送任何网络包，仍到达一次 `tryBreakBlock()`。程序在该回调处截断，没有执行方块删除。输出为：

```text
vanilla update reached tryBreakBlock; recovery protection=true
Confirmed exactly one pending break callback without any network packet
```

这确认了原版回调路径和现有保护覆盖缺口；**本次未执行完整游戏中的“挖掘 → START → 方块变化”复现**。

**建议：** 在实际执行破坏的边界增加受保护玩家检查，并明确处理 START 时已有的挖掘状态。仅继续拦截新包不足以解决问题。保持 Mixin 为窄适配层，策略继续由现有服务负责。

**建议补测：** 在明确标记的一次性测试世界中，先通过原版动作建立延迟挖掘，再开始 Stationary / Moving；覆盖后续 tick，断言目标方块和训练堆栈 NBT 不变，并验证停止后普通挖掘恢复正常。

### 2. [P2] 可合法保存的长配置名称导致整场统计丢失

**位置：** [LoadoutProfile.java:29](../src/main/java/dev/hotbarswaptrainer/profile/LoadoutProfile.java#L29)、[SessionMetadata.java:24](../src/main/java/dev/hotbarswaptrainer/stats/SessionMetadata.java#L24)、[SessionManager.java:306](../src/main/java/dev/hotbarswaptrainer/session/SessionManager.java#L306)。

配置层只要求名称非空；[编辑器名称输入框](../src/main/java/dev/hotbarswaptrainer/client/ProfileEditorScreen.java#L27)允许最多 1,048,576 个字符。一个 257 字符的名称能够通过配置验证、保存、重新加载和任务生成。

然而，结束会话时 `SessionMetadata` 将 `profileName` 限制为 256 字符。构造元数据抛出 `IllegalArgumentException`，控制流直接进入统计异常分支，根本不会调用 `results.save()`，Avg/Best 汇总也不会生成。此时会话已从 `sessions` 移除，无法再重试保存这场结果。

**最小复现：** 用默认配置打开 `ProfileRepository.Edit`，将名称设为 257 个 `a`，保存并重新加载；用它生成 Single 题目成功，再用同一名称构造 `SessionMetadata`。临时目录中的实际执行结果：

```text
257-character profile: save + reload + task generation succeeded
Session metadata rejected: Invalid metadata text
```

**影响：** 用户可以通过正常编辑流程创建一个能训练、却必然无法记录历史和 PB 的配置。背包恢复仍然独立完成，不属于背包数据损坏。

**建议：** 统一配置名称在编辑器、配置验证和历史序列化中的长度契约。最小改动是在配置验证和输入层采用相同上限，同时给已有超长名称提供明确错误；不要等训练结束才发现不可保存。若决定继续支持长名称，需要同步调整历史 codec 的限制，而不只是放宽元数据构造器。

**建议补测：** 256 / 257 字符边界，以及“保存配置 → 生成任务 → 构造会话结果 → HistoryStore 保存和重读”的跨模块测试。

### 3. [P2] 默认 UI 验收和 `--history` 入口仍调用失效的旧界面流程

**位置：** [ProfileEditorUiSmoke.java:85](../src/smoke/java/dev/hotbarswaptrainer/client/ProfileEditorUiSmoke.java#L85)、[run-profile-editor-ui.py:116](../scripts/run-profile-editor-ui.py#L116)。

启动脚本在没有 `--usability` 时仍启用 `hotbarswaptrainer.editorSmoke=true`，因此默认启动和单独的 `--history` 都会执行 `ProfileEditorUiSmoke`。

这个驱动第 0 步查找以 `Successful tasks` 开头的输入框。但当前 [TrainerOptionsScreen.java:22](../src/main/java/dev/hotbarswaptrainer/client/TrainerOptionsScreen.java#L22) 使用 `trainingCountHelp`，英文文案为 `Number of successful training tasks (0=unlimited)`。`input()` 精确使用 `startsWith(prefix)` 搜索，必然抛出 `Missing input: Successful tasks`。

后续还有同类失配：`Edit profile`、`Display name`、`Slot order`、历史分页和详情按钮已不属于当前界面；点击布局名称现在也只选择布局，不再进入编辑器。因此只替换第一个文案不能修复整个流程。

**复现入口：** 在满足现有隔离实例准备条件后，运行默认 `run-profile-editor-ui.py --launch`，或在新的允许命名实例中加 `--history`。进入测试世界后，驱动第 0 步即因上述查找失败终止。此结论由入口选择、当前资源和查找实现直接确认；**本次没有启动这两个游戏流程**。

**影响：** 仓库仍公开的验收入口不能验证当前实现。尤其旧流程后半部分包含实际 RESTORING / ERROR_LOCKED / LAN 和保存门禁检查，而更新的 usability 驱动没有完整替代这些场景。`smokeClasses` 编译成功不会发现控件文案或导航失配；旧产物的通过记录也不能证明当前流程仍可执行。

**建议：** 将仍需要的场景迁移到当前 UI 流程，保留一套可维护的驱动；若旧入口仅供历史记录，应明确停用，而不是继续默认启用。同步启动参数说明和验收文档。控件查询尽量依赖共享语义或本地化键，减少散落的过期英文文案。

**建议补测：** 在新的已标记隔离实例中实际执行每个仍公开的 UI 启动入口，并检查新生成的结果文件，而不仅看编译或进程退出码。

## 已确认但不列为新缺陷的限制

以下行为已有文档明确规定，不能仅因不符合另一种产品选择就算作代码错误。

- **超过 4096 次尝试的会话不保存整场历史。** 临时程序构造 4097 个成功任务后，`HistoryStore.save()` 抛出 `History task limit exceeded`，重读结果为 0 条。这里的上限计算所有尝试，包括 INVALID 和 STOPPED；UI 则允许无限练习和任意非负 int 目标。[现有契约](m2-d-history.md#L24)明确接受超限后的历史缺失。优点是存储解析和资源使用有界；缺点是用户可能练完很长一场才知道结果无法保存。若长时间训练是常用场景，建议单独讨论分段保存或提前提示；本次不擅自改变题数或统计语义。
- **无关文件的读取错误会阻止其他配置进入编辑。** 加入一个非法 UTF-8 的 `unrelated.json` 后，正常配置仍能出现在 `loadAll()` 中，但 `beginEdit()` 会失败。这符合[唯一性扫描的既定策略](m2-b-profile-editing.md#L78)。优点是无法确认 ID 唯一性时拒绝写入；缺点是单个坏文件会扩大到整个编辑流程。保留当前安全策略是合理的，不建议简单吞掉读取异常。

## 审查范围与架构评价

覆盖了 84 个生产 Java 文件、22 个单元测试文件、24 个 smoke Java 文件，以及资源、8 个 Python 脚本、JDK 启动脚本和 Gradle/build-support 构建逻辑。重点交叉核对了：

| 区域 | 审查重点 | 结果 |
| --- | --- | --- |
| profile / task | 配置验证、不可变快照、NBT、任务可解性、守恒、编辑冲突和原子保存 | 发现名称长度与统计契约冲突；未发现其他可确认问题 |
| inventory / session / input / environment | 双存档确认、失败后保护、恢复与统计隔离、状态转换、移动模式、输入计数 | 发现延迟挖掘保护缺口；未确认其他背包丢失路径 |
| mixin / network / client | 原版入口覆盖、协议和握手、输入门禁、配置持久化、编辑状态、HUD/PB | 生产问题见前两项；没有把游戏体验未经验证的猜测列为缺陷 |
| stats | 编解码、上限、幂等写入、过滤和 PB 环境隔离 | 名称边界问题见第 2 项；容量限制属于已记录策略 |
| smoke / scripts / build / resources | 隔离与所有权检查、故障注入边界、验收结果、构建产物隔离 | 发现旧 UI 验收入口失效 |

模块职责和依赖方向整体明确。任务域不依赖玩家、文件和 UI；恢复确认与统计/通知分离，是应继续保留的边界。没有发现需要通过大规模重构解决的已确认循环依赖或反向模块依赖。

本次最值得补强的是**跨模块契约测试**和**保护生效前已经排队的原版行为**。现有测试较充分地验证了模块内部，但模块各自接受的数据范围仍可能互不兼容；网络包被拦截也不等于所有后续原版 tick 行为都被覆盖。具体补测应围绕上述发现展开，无需先引入新框架或全面拆分模块。

## 本次实际执行的验证

| 检查 | 结果与范围 |
| --- | --- |
| `python3 scripts/check-boundaries.py` | 通过：84 个生产 Java 文件、12 个包、30 条有向包依赖；未报告循环或受检查的边界违规 |
| `scripts/gradle-jdk8.sh --offline test build smokeClasses` | 通过；其中 test 和多数编译任务使用了 up-to-date 输出，因此另行强制执行测试 |
| `scripts/gradle-jdk8.sh --offline test --rerun-tasks` | 实际重新编译并运行：22 个测试类、129 个测试，0 失败、0 错误、0 跳过 |
| 8 个 Python 脚本的 `ast.parse` | 全部语法检查通过；未执行会启动游戏或操作存档的脚本 |
| 资源 JSON 解析、中英文语言键集合比较 | 通过，键集合一致 |
| 生产 JAR 检查 | 122 个 class，全部 class version 52；未发现 smoke 包或 UiSmoke 驱动类泄漏 |
| 临时 Java 复现 | 确认长名称元数据失败；确认原版延迟挖掘回调可在保护标记存在时发生；另外核对历史容量和损坏文件扫描策略 |

首轮 Gradle 启动因沙箱禁止本地 socket 而失败；随后在允许 Gradle 本地通信的执行环境中成功运行。该首次失败不是项目编译缺陷。

临时复现代码和夹具只写入系统临时目录，不进入仓库。深层 JSON 的额外排查被现有解析路径转化为配置错误，没有发现崩溃，故未列为问题。

**未执行：** 新一轮 Minecraft 实际启动、真实键鼠/焦点/显示时序检查、进程中断与重新加载、存档恢复故障矩阵、最新产物的 UI 验收。仓库既有验收记录仅作为历史上下文，没有计入本次通过项。上述检查通过不代表这些运行时验收已经通过。
