# 4. 将初始加载改为致命、延迟落盘的启动事务

## 修改文件

- `leaf-server/src/main/java/org/dreeam/leaf/config/LeafConfig.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/util/ConfigFileIO.java`
- `leaf-server/src/minecraft/java/net/minecraft/server/Main.java`（验证现有顶层 fatal path；仅在日志需要补充时修改）
- 新增 `paper-server/src/test/java/org/dreeam/leaf/config/LeafConfigInitializationTest.java`

## 为什么修改

当前 `loadConfig()` 捕获异常后继续启动，且在 global registry 回调成功前保存 world defaults，可能生成部分 Leaf 配置。

## 具体修改

- `LeafConfig.loadConfig()`：
  - 不再捕获后返回；
  - 记录完整配置错误后重新抛出 `IllegalStateException`；
  - 让异常到达 `Main` 已有的最外层 `catch (Throwable)`，终止服务器启动。
- 初始加载顺序固定为：
  1. 创建共享 backup session；
  2. 执行 `purgeOutdated(session)`；
  3. 创建 `config/` 目录；
  4. 仅解析 raw `leaf-global.yml` 和 raw `leaf-world-defaults.yml` 为 `ConfigFile`；
  5. 立即调用各 global/world-default module class 的静态 migration 方法以收集 Leaf path operation，并执行 Gale global/defaults migration；
  6. 仅在迁移完成后构造 `LeafGlobalConfig`、绑定 global modules、运行 `onLoaded()`；
  7. 构造并绑定 world-defaults template；
  8. 在 bootstrap 后运行 `onRegistriesLoaded()`；
  9. 最后一次性保存 global + world-defaults。
- “迁移在加载候选前”的精确定义：
  - `ConfigFileIO.load()` 是 raw YAML 解析，必须先发生；
  - module migration operation 收集、验证和 migration lookup 构造必须在 `LeafGlobalConfig` 构造、`loadWorldDefaults()`、任何 `addDefault()`、任何 binder 读取之前发生；
  - 这样自动 defaults 永远不会影响迁移冲突判断。
- 删除 `loadConfig()` 中的 `worldDefaultsConfig.saveConfig()`。
- `loadAfterBootstrap()` 改为使用一次 `ConfigFileIO` 事务保存 global + world-defaults，而非只保存 global。
- 若任一 raw parse、Leaf migration、Gale migration 的致命部分、binder、`onLoaded()`、`onRegistriesLoaded()` 或最终写入失败：
  - 停止启动；
  - 在最终保存开始前失败时，新的 Leaf global/defaults 文件不生成；
  - 最终逐文件保存中的非 `AccessDeniedException` 仍停止启动，但不提供跨文件 rollback；已成功保存的前序文件保持已保存状态。
- Gale 文件单阶段失败仍按子计划 08 的可恢复规则处理，不将该失败视为 Leaf 初始化致命错误。

## 与现有代码交互

- `Main` 已在 bootstrap 前调用 `loadConfig()`、bootstrap 后调用 `loadAfterBootstrap()`。
- `LeafGlobalConfig` 继续设置版本和 comments，并只在全部候选成功后进入最终保存阶段；`AccessDeniedException` 时版本/comments 仅保留在内存。
- `ConfigFileIO` 提供 Paper 风格的逐文件保存：仅在所有 Leaf 候选成功后开始保存；`AccessDeniedException` 记录 warning 并继续使用内存配置，不创建 rollback 文件。
- 保存 helper 为本次保存收集每个 target 的结果：`SAVED`、`ACCESS_DENIED` 或 `FAILED`。非 `AccessDeniedException` 终止启动时，抛出的异常和控制台错误必须列出已保存文件、未保存的权限拒绝文件及发生失败的文件；权限拒绝 warning 也必须逐文件列出未持久化 target。

## 测试

- 使用临时目录分别制造：
  - malformed Leaf YAML；
  - 无效 enum；
  - Leaf path migration 的 section/source 错误；
  - module callback 抛错；
  - 非 `AccessDeniedException` 的最终保存失败；
  - `AccessDeniedException` 的最终保存失败。
- 前四类确认：异常到达调用方，且在最终保存前不生成/覆盖 Leaf 文件。非 `AccessDeniedException` 保存失败确认：异常到达调用方，已完成的前序逐文件保存不回滚。`AccessDeniedException` 确认：启动继续、内存配置完整可用、日志给出管理员可操作的 warning。
- 成功路径确认：global/defaults 同时生成，comments 完整。

## 可能的失败模式

- 保留 catch-and-return 会导致无效配置下继续启动。
- 在 migration 前 materialize defaults 会污染冲突处理。
- `AccessDeniedException` 时配置不会持久化，下一次启动仍会从磁盘旧值加载；管理员必须修复权限并核对 warning。
