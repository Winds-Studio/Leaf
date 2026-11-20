# Leaf 配置重构：完整修订实施计划

本目录将原始计划按其章节完整拆分；除文件拆分和索引外，不改变原计划的决策与范围。

## 现状核查、需求含义与冲突处理

已确认当前源码存在以下行为：

- `ConfigBinder.validateField()` 在初始加载、每次重载、每个世界 defaults/override 绑定中重复执行。
- global/defaults 选项在重载时缺失，会以代码默认值重新写入，而不是保留重载前运行值。
- `reloadAsync()` 提交到 `MinecraftServer` executor，但没有拒绝并发/排队重载，也没有跨“候选读取—字段提交—文件保存”全过程的事务锁。
- `commitReload()` 在字段变更和回调后才调用 `saveAtomically()`；`PendingValue` 的旧值在候选收集阶段创建。
- Leaf migration 必须先有原始 `ConfigFile` 才能运行；原计划中的“迁移在加载候选文件前”需要精确定义为：迁移必须发生在 raw `ConfigFile` 解析之后、任何 `LeafGlobalConfig` / binder 默认值注入之前。
- `LeafConfigMigration` 当前只覆盖 global/defaults，不处理已存在的 `leaf-world.yml`。
- `loadWorldConfig()` 已正确创建独立世界对象并复制 defaults；该行为保留并加测试，不改为共享 defaults。
- `purgeOutdated()` 和 `GaleConfigMigration.archive()` 各自生成时间戳目录，且 Gale 每次 archive 都可能使用不同目录。
- 测试依赖已存在于 `leaf-server/build.gradle.kts`，且 `leaf-server` 的 test source set 映射到 `paper-server/src/test/java`；无需新增依赖。

冲突处理：

- “初始化失败停止服务器”适用于 Leaf 原始配置解析、Leaf path migration、配置绑定、模块回调、最终保存和 legacy purge 失败；这些失败都阻止启动。
- Gale migration 保持“单源文件阶段失败可恢复”：该阶段回滚其迁移值、记录错误、继续其他 Gale 文件阶段和正常 Leaf 启动。这符合先前“malformed Gale world config 跳过迁移但仍归档”的要求。
- Leaf 配置保存采用 Paper 风格：在所有候选文件成功加载、绑定和验证后，直接保存各 Leaf 文件；不创建 staged/rollback 文件。`AccessDeniedException` 只记录警告并继续使用完整的内存配置，其他保存错误仍按其所在流程报告。reload 使用单一事务锁和禁止并发 reload，避免候选值与字段提交之间的 ABA。

## 子计划索引

1. [模块契约校验](01-module-contract-validation.md)
2. [注解模块与不可热重载](02-annotation-modules-and-hot-reload.md)
3. [binder 候选值与快照](03-binder-candidates-and-snapshots.md)
4. [启动事务](04-fatal-startup-transaction.md)
5. [备份 session](05-backup-session.md)
6. [world config 生命周期](06-world-config-lifecycle.md)
7. [reload 事务](07-reload-transaction.md)
8. [Leaf/Gale migration stage](08-migration-stages.md)
9. [删除 Gale runtime/API](09-remove-gale-runtime-api.md)
10. [测试与一致性审查](10-tests-and-consistency.md)
