# 11. 实施阶段与流程

本文件是每轮重写会话的输入，只规定顺序、范围、检查和遇到问题时的处理流程，不新增行为决定。行为以 00 的“已决定”条目为准；子计划提供细节，与 00 冲突时以 00 为准，并把冲突记入 00 的待处理问题。

## 通用规则

### 每轮会话的输入

- `AGENTS.md`、00、本文件，以及本阶段列出的子计划。
- 本阶段的决策编号清单。只实现清单内的条目；清单外的问题只记录，不修改。

### 开始前

- 记录各仓库的起点提交，便于按阶段查看 diff。applied source 分布在多个独立 git 仓库：
  - 主仓库：`leaf-server/src/main/java/**`（Leaf 配置框架与模块）；
  - `leaf-server/src/minecraft/java`（Minecraft 源码）；
  - `paper-server`、`paper-api`。
- 阶段进行中不应用、不重建 patch，否则会覆盖 applied source。

### 补丁归属

- Minecraft 源码与 paper-server 中的配置改动最终并入 Leaf config changes 补丁：
  - Minecraft：`leaf-server/minecraft-patches/features/0333-Leaf-config-changes.patch`（`leaf-server/src/minecraft/java` 中的提交 `Leaf config changes`）；
  - paper-server：`leaf-server/paper-patches/features/0069-Leaf-config-changes.patch`（`paper-server` 中的提交 `Leaf config changes`）。
- paper-api：目前没有对应补丁，新建一个 Leaf config changes 补丁（`leaf-api/paper-patches/features/`，当前最后一个为 `0023`），收录 P2-11 删除 `Server.Spigot#getGaleConfig()` 等 API 侧配置改动。补丁由所有者在收尾时创建。
- 这些仓库中只做配置相关的改动，不顺带修改其他代码，便于整体并入上述补丁。即使改动的行最初由其他特性补丁引入（如安全种子、各模块的读取点），也归入 Leaf config changes。
- agent 不提交、不生成补丁。全部阶段完成后，由所有者把改动并入上述提交并重建补丁，供整体 review。
- 阶段之间若需要分阶段的 diff，可由所有者在嵌套仓库中以 fixup 提交（目标为 `Leaf config changes`）记录，最后统一合并。

### 验证

- 每阶段同时编写该阶段的测试（见 10 的测试清单），不集中到最后再写。
- 每阶段结束时由实施会话自行编译并运行 `LeafTestSuite`（命令见 10），并核对报告中实际执行了本阶段的测试类。编译或测试未通过时不进入下一阶段；失败属于本阶段范围则修复，不属于则按“遇到问题时”处理。完整服务器启动仍由所有者在阶段 6 进行。
- patch 生成、应用、重建任务在任何情况下都不执行。

### 完成报告（每阶段）

1. 决策编号 → 修改的文件与方法；
2. 未完成的条目及原因；
3. 与计划不一致之处（实现细节自行决定的部分也列出）；
4. 新增的待处理问题（已写入 00）；
5. 需要所有者执行的验证。

同时更新相关子计划的“实现状态”和 00 的“实施记录”，让下一轮从准确的状态开始。

## 遇到问题时

先分类，再处理：

| 情况 | 处理 |
|---|---|
| 实现细节，决策未涉及也不冲突（命名、帮助方法位置、内部结构） | 自行决定，写入完成报告 |
| 与已有决策冲突、计划写错、需要新的行为决定 | 停止该条目，不自行变通，也不只留 `TODO` 继续；按 `AGENTS.md` 的归因格式写入 00 待处理问题，附证据与可选方案。无依赖的条目可继续，有依赖的暂停 |
| 认为某个已有决策不对 | 只报告证据，不改代码，不在计划中重开 |
| 超出范围的发现（模块自身逻辑缺陷、无关代码） | 只记录到 00 待处理问题，不修改 |
| 依赖库的实际行为与计划描述不符 | 按第二行处理；以测试或字节码为证据 |

## 阶段

阶段按依赖排序，决策编号不代表实施顺序。

### 阶段 0：恢复可编译

- 决策：09 与 P2-11（删除 Gale runtime 与 `getGaleConfig()`；`finalizeMigration()` 调用保留到阶段 5）；P1-6（转换 `LocatorBarRange`、`DensityFunctionCompiler`，更新 Gale 映射模块换包后的引用，删除 `run-async-tasks-sync`）；P1-10；P1-13（SecureSeed 改回 global，`enabled` 加 `@HotReloadUnsupported`）；P2-30（删除 `Knockback.flushKnockback`）。
- 涉及仓库：全部四个。
- 检查：
  - 编译通过；
  - `python3 plans/leaf-config-refactor/tools/config_paths.py`（对比 ver/26.2）：`scope changed` 为空；`only in working tree` 只剩 Gale 映射引入的选项（P2-30 已删除）；`default changed` 只剩已核对的等价项（见 00 实施记录）。

### 阶段 1：库行为确认与 binder

- 先写库行为测试（ConfigurationMaster `v2.0.0-rc.3`），确认后再改 binder：
  - `addDefault()` 是否用读入值覆盖此前 `set()` 写入的值（P1-8）；
  - `set(path, null)` 后读入值是否仍可被 `contains()`/`get()` 读到；
  - `ConfigFile.loadConfig()` 对缺失文件是否立即建文件（P1-9）；
  - 保存时是否只输出已登记的键与注释（P1-11 写回 override、P2-17）。
  结论写入 00 实施记录。与计划描述不符时按“遇到问题时”处理。
- 接入测试套件：`LeafTestSuite` 的 `@SelectPackages` 加入 `org.dreeam.leaf.config`，测试加 `@Tag("LeafTest")`（见 10）。
- 决策：P2-25（合并绑定路径）；P2-14 的 binder 部分（只保留 `PendingValue`）；P1-8（默认值只填真正缺失的键；迁移路径的端到端测试在阶段 4）；P2-7、P2-9；P1-3；P2-22（`HotReloadUnsupported` Javadoc）；P2-13 的 binder 部分（null 默认值、重复的 `@ConfigClassInfo` 检查、模块类型相等检查、快照缺失检查）；P2-15 的 binder 部分（`GLOBAL_DEFAULT_VALUES` 等、`discoverWorldModules()` 早退、`loadAfterBootstrap()` 的日志）。
- 测试：`ConfigModuleContractTest`（与 P2-13 删除运行时检查放在同一阶段，避免出现两边都不保护的窗口）、`ConfigBinderReloadTest` 的 binder 部分。

### 阶段 2：模块规则

- 决策：P1-16（派生值移入 `@DoNotLoad` 字段，并把 Minecraft/Paper 中的读取点改读派生字段）；P1-7；P2-23（`Level.leafConfig` 改为 `private final`）。`ThrottleNaturalMobSpawning` 保持手工读取。
- 检查：
  - `config_paths.py --base <阶段起点提交>` 各节均为空；
  - 对 P1-16 每个字段搜索全部读取点，确认运行期读取的是派生字段。
- 测试：02 的 FastRNG 与刷怪动态键测试。

### 阶段 3：文件 IO、启动与备份

- 决策：P1-9、P1-12、P2-10、P2-4；P2-13 的 `ConfigFileIO.save()` 重复检查；P2-3 保持现状（只确认）。
- 测试：`ConfigFileIOTest`、`LeafConfigInitializationTest` 中与版本无关的部分、`ConfigBackupSessionTest` 中与归档无关的部分。

### 阶段 4：版本与迁移

- 决策：P0-1（含 `initWorldConfig()` 按文件版本迁移 override，以及 P2-15 删除旧版本辅助代码）；P1-11（含 override 补写版本）；P2-2、P2-8、P2-21、P2-12；P1-4（定义检查移入测试，保留数据检查，新增冲突 WARN）；P2-14 的迁移部分（删除 `LeafConfigMigration` 与 Gale 的回滚）；P0-3；08 的 Gale 迁移规则（null 视同缺键、按旧字段类型读取、非法映射值使加载失败）；P2-13 的 Gale 部分（`resolveMappings()` 直接抛出）；P2-18；P2-1。
- 测试：`LeafConfigMigrationTest`、`GaleConfigMigrationTest`、`LeafWorldOverrideMigrationTest` 中加载时的部分；P1-8 的端到端测试（绑定字段、保存后 YAML、再次读取三者一致）。

### 阶段 5：世界生命周期、reload 与归档

- 决策：同步 reload（删除 future、并发标记与锁）；P0-2；P1-2；P2-19；P1-1（分阶段归档，删除 `MinecraftServer` 中的 `finalizeMigration()` 调用与全局遍历）。P1-5、P2-20 保持现状（只确认）。
- 测试：`LeafConfigReloadTransactionTest`、归档相关测试、`LeafWorldOverrideMigrationTest` 中 reload 的部分。

### 阶段 6：收尾

- 按 10 做一致性检查；运行全部 Leaf 测试，并核对测试报告中实际执行了 10 列出的各测试类（`LeafTestSuite` 设置了 `failIfNoTests = false`）。
- `config_paths.py` 对比 ver/26.2 的最终结果写入实施记录。
- 所有者按“补丁归属”把 Minecraft 与 paper-server 的配置改动并入 Leaf config changes 补丁、为 paper-api 新建 Leaf config changes 补丁并重建，对全部重构做整体 review。
- 由所有者完整启动服务器，覆盖：全新安装、从 ver/26.2 配置升级、带 Gale 配置升级、`/leaf reload`、插件晚加载世界。

## 每阶段的审查

- 另开新上下文的会话审查，不由编写代码的会话自审。
- 只对照本阶段的决策清单检查：每条是否按原文实现、是否多做。不做开放式的“再找找问题”。
- 固定检查项：
  - 用户可见的配置路径、默认值未变化（`config_paths.py`）；注释未变化（人工检查 diff）；
  - 热路径未新增间接调用，模块字段仍被直接读取；
  - 未新增针对开发者定义错误的运行时检查（P2-13），用户数据检查保留；
  - 未改变线程归属，未新增异步执行；
  - 未修改 patch 文件，未执行 patch 任务；
  - Minecraft、paper-server、paper-api 中只有配置相关的改动，可整体并入各自的 Leaf config changes 补丁。
- 审查结论只有两类：不符合决策（必须修改），或新问题（写入 00 待处理问题，由所有者决定）。

## 工具

- `tools/config_paths.py`：比较某个 git 提交与工作区中的配置选项路径、作用域、类型和默认值（只读）。手工读取的键（如 `ThrottleNaturalMobSpawning` 的动态键）不在覆盖范围内；默认值按源码文本比较，结果需人工核对。
