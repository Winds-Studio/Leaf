# 7. 重载改为同步、两阶段（候选 → 提交）、不回滚的逻辑事务

## 修改文件

- `leaf-server/src/main/java/org/dreeam/leaf/config/LeafConfig.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/ConfigBinder.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/util/ConfigFileIO.java`
- `leaf-server/src/main/java/org/dreeam/leaf/command/subcommands/ReloadCommand.java`
- 新增 `paper-server/src/test/java/org/dreeam/leaf/config/LeafConfigReloadTransactionTest.java`
- 新增 `paper-server/src/test/java/org/dreeam/leaf/config/util/ConfigFileIOTest.java`

（原计划中的 `LeafConfigReloadTransaction` 类未单独创建，逻辑位于 `LeafConfig.reloadAsync()` / `commitReload()`；前者按下方决定改为同步 `reload(sender)`。）

## 为什么修改

reload 不能与世界和异步配置状态竞争；旧实现在候选阶段就生成带旧值的 `PendingValue`，且文件保存和字段提交没有单一事务边界。

## 实现状态

### 线程与并发（当前实现，将按下方决定删除）

- `reloadAsync(sender)` 返回 `CompletableFuture<Void>`，通过 `CompletableFuture.runAsync(task, server)` 交给 `MinecraftServer` executor。从服务器线程的非任务上下文调用时，`execute()` 同步执行；其他情况在下一次任务处理时执行。
- YAML 解析、世界遍历、字段写入、回调、保存都在服务器线程执行，不移到通用后台线程。
- `RELOAD_QUEUED_OR_RUNNING.compareAndSet(false, true)` 失败时向 sender 报告“already in progress”，返回已完成的 future，不排队。
- 已进入任务体时由 `finally` 释放锁并清除标记；出队前原始 future 被取消会跳过任务体，标记不再释放（原 P2-6）。
- `RELOAD_TRANSACTION_LOCK`（`ReentrantLock`）包住整个任务。由于任务总在服务器线程上且标记已禁止并发，该锁目前是冗余防护，保留无害。
- async 模块均为类级不可热重载，reload 不重建任何 executor、线程数或 parallel world ticking 状态。

### 候选收集：已完成

1. raw load global 与 world-defaults candidate；
2. `new LeafGlobalConfig(candidate, false)`（不覆盖内存中的旧版本记录）；
3. `reloadWorldDefaults()`：按 03 的 fallback 规则绑定 defaults candidate；
4. 收集 global 与 defaults 的 `ReloadValue`（P2-14 后直接收集为 `PendingValue`）；
5. 遍历 `server.getAllLevels()`，`loadWorldConfig(dir, candidateDefaults, false)` 构造独立 candidate 并收集候选值；
6. 上述任一步骤失败 → 不修改运行字段或 config 引用，不运行回调，不调用 `save()`。若 global/defaults 原本缺失，底层加载可能已经留下空文件；候选完全不写磁盘仍待 04 P1-9。

### 提交与保存（当前实现；目标顺序见 P1-2）

`commitReload()`：

1. 把 `ReloadValue` 转为 `PendingValue`（不记录旧值；P2-14 后删除此步）；
2. 切换 `globalConfig`、defaults 与每个世界的 `configFile` 引用（P2-19：删除对各世界的切换及 `WorldReload`，只保留 global 与 defaults）；
3. 依次 `apply()` 所有字段（包括不可热重载字段的快照恢复值）；
4. `ConfigFileIO.save(defaults, global)`：
   - 权限拒绝：sender 收到黄色提示，列出仅在内存中生效的文件；
   - 其他失败：抛 `ConfigSaveException`，sender 收到红色提示与完整文件状态，控制台记录异常；**当前实现此时不会执行回调**；
5. 运行 `runGlobalModuleCallbacks(true)` 和 `runAfterBootstrapCallbacks(true)`（跳过类级不可热重载模块）；异常被捕获，sender 收到“post-reload processing failed”提示。

- 不回滚：提交阶段不创建回滚副本，保存失败后不恢复字段、config 引用或已成功保存的文件。原标题中的“可回滚”已更正。
- 当前 world override 文件不作为 reload 的保存目标。P0-2 修改后，已有 override 的版本落后即需保存，即使没有命中 rename；当前版本的不写回，缺少文件的不创建，不补全默认项。

## 去除异步 reload，与 Paper 一致同步执行（已决定，阶段 5 已实施）

Paper `/paper reload` 在命令处理中直接调用 `PaperConfigurations.reloadConfigs(server)`，Bukkit `CraftServer.reload()` 也顺序同步调用；二者都没有排队、future、并发标记、锁或服务器状态门槛。

- `reloadAsync(sender)` 改为同步的 `reload(sender)`，在调用线程直接执行候选收集、提交、回调与保存，不返回 future。`/leaf reload`（`ReloadCommand`，唯一调用方）在服务器线程的命令处理中调用。
- 删除 `CompletableFuture.runAsync(..., server)`、`RELOAD_QUEUED_OR_RUNNING`、`RELOAD_TRANSACTION_LOCK`、“already in progress”提示与不可达的 `RejectedExecutionException` 分支。不新增线程检查或启动、停止状态门槛。
- 原 P2-5（停止后 `execute()` 在调用线程运行、拒绝分支不可达）与 P2-6（排队 future 被提前完成导致门闩不释放）随之消失。
- 两阶段结构、P0-2、P1-2 的顺序和报告方式不变。

## P0-2 override 迁移（已决定，阶段 5 已实施）

- 候选阶段：先按 raw global 的版本迁移 global/defaults，再对 `server.getAllLevels()` 中已有 override 的世界按文件自身版本迁移，均在绑定前处理。版本按 00“版本号规则”处理（缺失视为当前版本、无法解析则 reload 失败）；候选失败不写盘。
- 提交阶段：需升级的 override → defaults → global；每份 override 的内容与版本一起写。单个 override 权限拒绝不阻止其余目标保存，也不影响 global/defaults 的进度。
- reload 不重放 Gale 转换。若新 override 因权限拒绝只在内存中生效，reload 后使用 defaults，下次加载该世界时从保留的源文件重试。

## P1-2 首个回调失败即停止，最后保存（已决定，阶段 5 已实施）

- 候选读取、迁移、校验全部成功后才统一应用字段；应拒绝的非法值在绑定阶段检查。随后依次执行两组回调，首个异常即停止，不继续其余回调，也不进入保存阶段。报告失败并记录 ERROR 和异常堆栈；不回滚已经应用的字段或回调副作用。
- 回调全部成功后才保存。`AccessDeniedException` 沿用现有 warning 和内存配置；其他保存异常在 reload 入口报告失败，记录 ERROR 和完整异常堆栈，不发成功消息，不重试、不回滚。保留现有逐文件保存报告，不增加回调异常汇总机制。

## 待处理问题

### 其他边界

- 手工读取的动态 key（`ThrottleNaturalMobSpawning`）仍不适用 binder 的缺失键回退；按 P1-2 调整顺序后，回调补写的默认值随最终保存写入（见 02）。
- 异步线程可见性（风险，非本次范围）：字段由服务器线程写入，而 async pathfinding、tracker 等线程可能读取其他可重载模块的非 volatile static 字段，没有 happens-before 保证，多个相关字段可能被看到不一致的组合。现有行为与旧实现一致；`List` 字段必须整体替换引用，不得原地修改（当前 `apply()` 满足）。

## 测试

- 同步执行：`reload(sender)` 返回时字段、回调与保存均已完成；连续两次调用依次完整执行。
- 缺失 global + 后续 world candidate enum 非法：没有字段提交；原有文件内容不变、缺失文件仍不存在（P1-9 修复后）。
- `AccessDeniedException`：字段与 config 引用已更新，reload 继续，提示列出未持久化文件。
- 最终保存发生非权限异常：回调已经完成，字段与已保存文件不回滚；控制台有 ERROR 和堆栈，sender 不收到成功消息。
- 删除 global/defaults option：写回 reload 前的值。
- async module 配置修改：运行字段保持首次值，async 回调不运行；YAML 保存新值。
- 回调异常：后续回调不执行，不保存文件、不报成功；记录异常堆栈，已提交字段不回滚。
- global 已是当前版本、已加载世界的 override 仍旧：reload 按其自身版本迁移并保存，即使未命中 rename 也写回版本。候选失败不写入；单个 override 权限拒绝不阻止其他目标保存，未加载世界的文件不读写（修改后）。

## 可能的失败模式

- 多文件保存不是事务：设计只保证候选全部成功前不保存任何 Leaf 文件。
- 回调可能有外部副作用，它们在配置提交之后运行且不参与回滚；异步或启动期模块通过不可热重载避免进入此路径。
- 在非服务器线程调用 `reload(sender)` 会在该线程写字段和执行回调；与 Paper 相同，不支持这种调用。
