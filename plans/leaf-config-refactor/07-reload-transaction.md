# 7. 重载改为串行、两阶段、可回滚的逻辑事务

## 修改文件

- `leaf-server/src/main/java/org/dreeam/leaf/config/LeafConfig.java`
- 新增 `leaf-server/src/main/java/org/dreeam/leaf/config/LeafConfigReloadTransaction.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/ConfigBinder.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/util/ConfigFileIO.java`
- `leaf-server/src/main/java/org/dreeam/leaf/command/subcommands/ReloadCommand.java`
- 新增 `paper-server/src/test/java/org/dreeam/leaf/config/LeafConfigReloadTransactionTest.java`
- 新增 `paper-server/src/test/java/org/dreeam/leaf/config/util/ConfigFileIOTest.java`

## 为什么修改

重载的异步入口必须不与世界/异步配置状态竞争。当前候选阶段即创建带旧值的 `PendingValue`，且文件保存和字段提交没有单一事务边界。

## 具体修改

### 7.1 线程与并发策略

- `reloadAsync()` 保持返回 `CompletableFuture<Void>`，但“async”仅表示命令不阻塞调用方；整个 reload task 仍提交给 `MinecraftServer` executor。
- 不将 YAML 解析、世界遍历、field mutation、回调或文件 commit 移到通用后台线程：
  - 它们读取/修改 `ServerLevel`、global static config、callbacks 和世界对象；
  - 这些对象没有异步线程安全保证。
- 添加 `AtomicBoolean reloadQueuedOrRunning`：
  - 命令触发时 `compareAndSet(false, true)`；
  - 已有 reload 时立即向 sender 返回“reload already in progress”；
  - task 完成、失败或取消后在 finally 清除。
- 若 `MinecraftServer` executor 在 task 开始前拒绝提交，立即清除 `reloadQueuedOrRunning`、完成 future 为异常并向 sender 报告；不能依赖 task 内的 finally。
- `LeafConfigReloadTransaction` 使用单一 `ReentrantLock` 包住候选收集、文件准备、字段提交、回调、保存/回滚全过程。
- 因 async 模块均为类级不可热重载，reload transaction 不重建任何 async executor、线程数或 parallel world ticking 状态。

### 7.2 候选收集与延迟保存

- 在 transaction lock 内执行：
  1. raw load global candidate；
  2. raw load world-defaults candidate；
  3. 使用子计划 03 的 fallback 规则绑定 defaults candidate；
  4. 收集 global/defaults 的 `ReloadCandidateValue`；
  5. 遍历所有 loaded worlds，构造独立 candidate world config 并收集候选值；
  6. 确认所有候选解析、类型转换和模块结构访问已成功；
  7. 仅在上述所有 Leaf 配置文件均成功加载、merge、绑定和验证后，准备最终 field set 与 global/defaults 直接保存；候选阶段不得创建或替换任何 Leaf target 文件。
- 候选阶段绝不：
  - set active fields；
  - 更换 active config file references；
  - 运行 callbacks；
  - 保存任何 target 文件。

### 7.3 最终提交与保存

- 在所有候选验证完成后：
  1. 在 lock 内将 active global/defaults/world `configFile` references 切换为 candidate；
  2. 在该时刻才将 `ReloadCandidateValue` 捕获为 applied value，记录真正的旧字段值；
  3. 应用所有字段，包括不可热重载字段的首次快照恢复值；
  4. 依次直接保存 global/defaults，并收集每个 target 的 `SAVED`、`ACCESS_DENIED` 或 `FAILED` 结果；只在 `AccessDeniedException` 时记录 warning 并继续使用内存配置，其他保存错误向调用方报告；
  5. 运行允许重载模块的 callbacks。
- 候选已通过预先验证，field set 不作为可失败阶段。不创建 rollback 副本，也不在保存失败后恢复字段、config-file reference 或已成功保存的另一个 Leaf 文件。
- 保存报告必须在错误消息中列出已保存、权限拒绝且未保存、以及失败的文件。非 `AccessDeniedException` 时，command sender 收到 reload 持久化失败消息，控制台异常附带完整文件结果；`AccessDeniedException` 时，sender 和控制台 warning 明确列出仅在内存中生效的文件。
- callback 在字段和文件保存后执行；callback 异常记录并报告 reload 后处理失败，但不重新运行旧 callback，也不回滚已提交字段或配置文件。
- `PendingValue` 旧值只在 commit 阶段捕获；因为 transaction lock 和 `reloadQueuedOrRunning` 禁止第二次 reload 介入，不存在 A→B→A 的 ABA restore。
- world override 文件从不作为 reload 的保存目标；只保存 global/defaults。

## 与现有代码交互

- `ReloadCommand` 继续调用 `LeafConfig.reloadAsync(sender)`，但会得到“in progress”结果而不是排队第二个 reload。
- `ConfigBinder` 只生成候选值；`LeafConfigReloadTransaction` 是唯一 active field 写入者。
- `ConfigFileIO` 的保存 helper 采用 Paper 风格的 `trySave` 语义；调用者只在所有候选验证完成后调用它。

## 测试

- 两次连续 reload：第二次被拒绝，不创建第二个事务。
- 任一 world candidate enum 非法：没有 global/defaults/world 字段变更，也没有文件写入。
- `AccessDeniedException` 保存：字段和 config-file references 已更新、reload 继续、日志报告未持久化文件。
- 非 `AccessDeniedException` 保存失败：字段和已成功保存的前序文件不回滚，错误必须列出已保存、权限拒绝和失败文件。
- 删除 global/defaults option：写回重载前值。
- async module 配置修改：active async 字段保持首次值，async callbacks 不运行。
- executor 拒绝 task：reload 标记立即清除，后续 reload 不会被永久拒绝。
- callback 异常：候选文件和字段已提交，错误被报告且不会触发旧 callback 重跑。

## 可能的失败模式

- 多文件保存不是事务；设计保证的是候选加载完成前不保存任何 Leaf target，且无并发 reload ABA。权限拒绝时使用内存配置，其他保存错误不回滚先前已完成的最终操作。
- callback 可能含外部副作用；它位于配置提交之后且不参与回滚，异步/启动期模块通过不可热重载避免进入此路径。
- 忘记在 finally 清理 `reloadQueuedOrRunning` 会永久拒绝后续 reload。
