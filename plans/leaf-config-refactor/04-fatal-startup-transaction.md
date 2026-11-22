# 4. 将初始加载改为致命、延迟落盘的启动事务

## 修改文件

- `leaf-server/src/main/java/org/dreeam/leaf/config/LeafConfig.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/LeafGlobalConfig.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/util/ConfigFileIO.java`
- `leaf-server/src/minecraft/java/net/minecraft/server/Main.java`（仅确认顶层 fatal path，无需修改）
- 新增 `paper-server/src/test/java/org/dreeam/leaf/config/LeafConfigInitializationTest.java`

## 为什么修改

旧实现中 `loadConfig()` 捕获异常后继续启动，且在 registry 回调成功前保存 world defaults，可能生成部分 Leaf 配置。

## 实现状态（当前源码）

### 致命失败

- `loadConfig()` / `loadAfterBootstrap()` 捕获异常后包装为 `IllegalStateException` 抛出，到达 `Main` 最外层 `catch (Throwable)`，终止启动。

### 初始加载顺序

`loadConfig()`（bootstrap 前）：

1. `ConfigBackupSession.create()`（目录延迟创建）；
2. `purgeOutdated(session)`，当前 IO 失败为致命错误；按 P2-10 改为警告并保留原文件；
3. 创建 `config/`；
4. `ConfigFileIO.load()` 解析 raw `leaf-global.yml`、`leaf-world-defaults.yml`（先用 SnakeYAML 做一次快速失败校验，code point 上限 100M）；
5. 发现 global/world modules；
6. `LeafConfigMigration.migrate()`：用 raw global 中的 `config-version` 判断，在内存中完成 Leaf path migration；
7. `GaleConfigMigration.migrate()`：Gale global/defaults 分阶段写入 raw Leaf 文件（见 08）；
8. `new LeafGlobalConfig(globalConfigFile, true)`：把旧版本存入内存 `previousConfigVersion`，在内存中把 `config-version` 设为当前版本，写入头部注释和分类标题；
9. 绑定 global modules → `onLoaded()`；
10. 绑定 world defaults → 记录 defaults 快照。

`loadAfterBootstrap()`（bootstrap 后）：

11. `onRegistriesLoaded()`；
12. 记录 global 首次快照；
13. `ConfigFileIO.save(defaults, global)`：先 defaults、后 global（带版本号的文件最后写）；
14. `GaleConfigMigration.recordLeafPersistence(report)`：权限拒绝的 Leaf 目标对应的 Gale 源文件不归档。

“迁移在候选加载之前”的精确定义：第 6、7 步发生在 raw 解析之后、任何 `LeafGlobalConfig` 构造、`addDefault()`、binder 读取之前，因此自动默认值不会影响迁移冲突判断。

### 保存语义

- 第 13 步才显式保存 global/defaults，但第 4 步读取缺失文件会先创建空文件。因此“验证失败不创建 Leaf 文件”是目标，不是当前保证（P1-9）。
- `ConfigFileIO.save()` 逐文件尝试全部目标：
  - `AccessDeniedException`（包括 cause 链中）：记录 warning，继续使用内存配置；
  - 其他异常：全部目标尝试完后抛 `ConfigSaveException`，消息与 `report().describe()` 列出每个目标的 `SAVED/ACCESS_DENIED/FAILED` → 启动终止；
  - 已保存的文件不回滚。

读取 raw global 的版本时按 00“版本号规则”处理：缺失则 WARN 并视为当前版本、不迁移；无法解析则启动失败；高于当前版本则 WARN（P2-2）、不迁移，第 8 步不把它改写为当前版本（P2-8）。

## P1-9 缺失文件在候选读取时即被创建（已决定，阶段 3 已实施）

**归因：Leaf 引入** — `ConfigFileIO.load()` 对非普通文件直接调用 ConfigurationMaster `v2.0.0-rc.3` 的 `ConfigFile.loadConfig(File)`；它先 `createFile()`，再 `loadContent()`。Paper 的 global 缺失路径先构造内存节点，逐世界模板则有不同的显式创建契约。

例如 global 缺失、随后解析 defaults 失败：字段未提交且未调用保存，global 空文件却已存在。已决定：区分已有文件和确实缺失的目标；后者只构建内存候选，保留首次生成语义，成功保存时才创建目标。目录或权限错误不能当作缺失。不以事后删除空文件代替：它可能是管理员原有的文件。

实现范围：只改 `ConfigFileIO.load()` 一处。`Files.notExists(path)` 时返回 `new ConfigFile(file)`，不调用 `loadConfig()`（其内部执行 `createFile()` 与 `loadContent()`）；其他情况走现有读取。权限不足无法判断时 `notExists` 为 false，目录等非普通文件在读取时报错，无需另行分类。`ConfigFile.save()` 用 `Files.newOutputStream` 创建文件，`ConfigFileIO.save()` 已创建父目录。启动、reload 与 Gale 世界转换都经此函数，不新增候选对象、创建标记或失败清理。新对象的 `isNew()` 为 false，只影响 ConfigurationMaster 的 example 写入；当前仅 `LeafConfigAccessor.getConfigSection()` 登记 example，且无模块调用。以后引入 example 时再处理。启动、reload 和 Gale 新 override 的验收见 07、08。

## 待处理问题与边界

- **P2-3（已决定：与 Paper 保持一致）** `ConfigFile.save()` 不是原子写：写入中途失败会留下半截 YAML，下次启动在第 4 步被判为 malformed 并终止启动。Paper 同样直接写目标文件，不改为临时文件加 `ATOMIC_MOVE`。
- **P0-4**（见 00；原 P2-12 的“global 不升版”已取代）defaults 与 global 各自记录自己的迁移进度；第 13 步仍按 defaults → global 保存，但互不牵制：defaults 被拒绝时它在磁盘上保持旧版本，下次启动重新迁移，global 照常写入自己的版本；内存使用当前配置。override 按文件自记进度，在世界实际加载时单独处理，不影响这一步，不增加 `Main` hook 或启动扫描。
- 第 14 步待改为依据保存结果处理 Gale global/defaults 归档（见 05），不再等待所有世界加载。
- **P2-10（已决定）purge 失败改为警告。** `purgeOutdated()` 只把 `pufferfish.yml`、`leaf.yml`、`leaf_config/` 移入备份目录，不读取、不转换其内容；新配置也不依赖它们。移动失败时文件留在原处，下次启动再尝试移动，结果相同。因此任一移动的 `IOException`（含权限拒绝）都记 WARN（文件路径与原因），继续处理其余文件和启动；“已移入备份目录”的提示只在确有文件移动成功时输出。

## 测试

- 使用临时目录分别制造：malformed Leaf YAML、无效 enum、Leaf path migration 的 section 源错误、module 回调抛错、非 `AccessDeniedException` 的保存失败、`AccessDeniedException` 的保存失败。
- 前四类：异常到达调用方。用缺失 global + malformed defaults 验证失败后 global 路径仍不存在，并逐字节比对原有文件未改写（P1-9 修复后）。
- 非 `AccessDeniedException`：异常到达调用方，已完成的前序文件不回滚，报告列出全部目标状态。
- `AccessDeniedException`：启动继续，内存配置完整，warning 列出未持久化文件，对应 Gale 源文件不归档。
- 成功路径：`loadAfterBootstrap()` 末先保存 defaults，后保存 global；注释完整，global 的 `config-version` 为当前版本。defaults 权限拒绝时 defaults 文件保持旧版本、global 照常记录新版本；之后某个 override 权限拒绝不改变已保存的 global/defaults 版本。

## 可能的失败模式

- 保留 catch-and-return 会导致无效配置下继续启动。
- 在 migration 前注入默认值会污染冲突处理。
- `AccessDeniedException` 时配置不会持久化，下一次启动仍会从磁盘旧值加载；管理员必须修复权限并核对 warning。
