# 10. 测试执行与最终一致性审查

## 修改文件

- `paper-server/src/test/java/org/dreeam/leaf/config/...` 下新增的测试文件（当前目录尚不存在，全部待创建）。
- 不需要修改 `leaf-server/build.gradle.kts`：JUnit、Mockito、ClassGraph、`@TempDir` 已存在；但 `tasks.test` 只选 `**/**TestSuite.class`。将 `org.dreeam.leaf.config` 加入 `paper-server/src/test/java/org/bukkit/support/suite/LeafTestSuite.java` 的 `@SelectPackages`，并给这些测试加 `@Tag("LeafTest")`（需要服务端测试环境时用 `@LeafTest`）。现有 suite 只选 `net.minecraft` 与 `gg.pufferfish.pufferfish`，仅创建测试类不会让它们自动进入该套件。

## 运行说明

编写并运行测试、编译源码均允许，在每个阶段结束时执行（见 11）。`AGENTS.md` 的补丁限制仍然有效：不执行补丁生成、应用、重建任务，也不运行与本任务无关的任务。

## 测试清单

| 测试类 | 覆盖内容 | 子计划 |
|---|---|---|
| `ConfigModuleContractTest` | 模块契约、async 类级标记、路径唯一、从运行时移入的定义检查（P2-13：null 默认值、`@ConfigClassInfo`、`migrate` 签名） | 01、02 |
| `ConfigBinderReloadTest` | 缺键与非法标量的区别、列表独立、按需快照、世界重新加载建立新基准；FastRNG 主开关可热重载，但有效 worldgen 决策跨 reload 不变 | 02、03 |
| `LeafConfigInitializationTest` | 致命失败、延迟落盘、保存报告、权限拒绝 | 04 |
| `ConfigBackupSessionTest` | 分阶段归档复用单一 backup root、延迟创建、后缀、非法路径 | 05 |
| `LeafWorldOverrideMigrationTest` | world 独立性、按自身版本 rename、缺版本 WARN 并补写、非法版本拒绝、更高版本不改写、无 rename 仍升级、可重复迁移、reload 后保持、晚加载走同一路径、无 override 不建文件 | 06 |
| `LeafConfigReloadTransactionTest` | 同步执行完成后返回、候选失败不创建缺失目标、仅迁移已加载世界、override 权限拒绝不牵连 global、保存与回调报告 | 07 |
| `ConfigFileIOTest` | malformed 检测、逐文件保存、`AccessDeniedException` 识别 | 04、07 |
| `LeafConfigMigrationTest` | 版本比较、四种作用域；实际迁移定义无同源多目标或跨操作父子重叠，正序链式迁移保存重读，显式 null 失败，冲突保留新值且仅异值告警；非默认迁移值经绑定、保存、重读仍保留 | 08 |
| `GaleConfigMigrationTest` | 原始登记列表无同源多目标；null 视同缺键、可转换值按旧类型导入、非法映射值使本次加载失败并保留源；分阶段无残留、已有 Leaf 优先、global/defaults 与新 override 的绑定差异、首次失败不留目标且能重试 | 08 |

## 验证命令

```text
./gradlew :leaf-server:test --tests org.bukkit.support.suite.LeafTestSuite
```

前提：02、09 中的编译阻塞项已处理，测试类已创建并接入 suite。核对测试报告中实际执行了上表各类（尤其契约和 reload 事务测试）；`LeafTestSuite` 设置了 `failIfNoTests = false`，仅凭任务成功不能确认覆盖。

最后：

```text
git diff --check
git status --short
```

## 一致性结论

- 保留：注解式配置；统一的 `CURRENT_CONFIG_VERSION`（3.1）；Gale 值进入 Leaf 文件；Leaf world override 优先于 Gale；Gale 文件归档；删除 Gale API/runtime；世界独立的 defaults merge；Paper 风格保存（权限拒绝时使用内存配置，无回滚）。
- 已有代码：binder 缺键回退、类级 async 模块不热重载、reload 不回滚、override rename 入口、统一 backup 目录与权限拒绝保留 Gale 源的分支。异步 reload 将改为同步（见 07）。不代表动态键、迁移值写回或 Gale 重试已经通过验收（见 02、04、07、08）。
- 已实施（阶段 4、5）：global、defaults、每份已有 override 各自记录自己的进度（P0-4）；实际加载世界时迁移，不做启动扫描。Gale 按对应阶段归档，不依赖一次性遍历。其余见 00 的 P0/P1/P2 列表。
- 不扩展：不修改 patch 文件、不生成 patch、不改变无关 Gale 功能、不自动补全 world override、不把 reload 移到后台线程。

## 可能的失败模式

- 测试环境可能缺少完整 Minecraft bootstrap 依赖；业务逻辑应保持在可通过临时 `ConfigFile` 测试的边界内。`LeafConfig` 中依赖 `MinecraftServer` / `ServerLevel` 的部分（reload 遍历已加载世界、`initWorldConfig()` 的世界加载时机）需要 package-private seam 或只做集成验证。
- 只运行新测试不能替代完整服务器启动验证；最终仍需仓库所有者进行完整集成验证。
