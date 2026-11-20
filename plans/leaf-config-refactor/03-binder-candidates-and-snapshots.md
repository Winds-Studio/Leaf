# 3. 重构 binder：首次值快照、重载删除保留与候选值模型

## 修改文件

- `leaf-server/src/main/java/org/dreeam/leaf/config/ConfigBinder.java`
- 新增 `leaf-server/src/main/java/org/dreeam/leaf/config/ReloadCandidateValue.java`
- 新增 `paper-server/src/test/java/org/dreeam/leaf/config/ConfigBinderReloadTest.java`

## 为什么修改

当前 `PendingValue` 在候选收集时保存旧值，且缺失 global/defaults 键会回退到代码默认值。重载应先构建纯候选数据，进入提交事务时再捕获旧运行值。

## 具体修改

- 保留 `GLOBAL_DEFAULT_VALUES`，仅用于首次启动和首次生成配置时的代码默认值。
- 新增首次成功初始化快照：
  - 全局 static 字段：以 `Field` 为键；
  - 世界模块字段：以 module instance identity + `Field` 为键；
  - 列表复制保存；
  - 仅覆盖字段级或类级 `@HotReloadUnsupported` 的字段。
  - global 快照只在 `onLoaded()` 和 `onRegistriesLoaded()` 均成功后记录；
  - world 快照只在 defaults merge 与已有 override bind 均成功后记录；
  - 初始化失败不记录快照；同一 field/实例只记录第一次成功值。
- 新增 `ReloadCandidateValue`：
  - 只保存 target、field、候选新值；
  - 不在收集阶段读取“旧值”；
  - 进入 reload commit lock 后才转成可回滚 applied value。
- global reload 的缺失键策略：
  - 从当前 active static 字段读取 fallback；
  - `readValue(..., writeDefault=true)` 使用该 fallback；
  - 用户删除 option 后，YAML 重新写入重载前的 active 值及注释，不恢复代码默认值。
- 初始加载的 path migration merge 策略：binder 请求 `new-path` 时，先读取同作用域 target 的显式新值；新值不存在时，由 `ConfigMigrationContext` 查找已收集的 `old-path` source 并使用其值；两者都不存在才使用代码默认值。该策略支持 global → global、global → world defaults、world defaults → global 与 world defaults → world defaults。
- world-defaults reload 的缺失键策略：
  - 新增 `bindWorldDefaultsForReload(candidateModule, activeModule, config)`；
  - 每个缺失 defaults option 使用 active defaults module 的当前字段值作为 fallback；
  - 只对 defaults 文件写回该旧值和 comment。
- world override 行为不变：
  - override 缺失键继续继承 candidate world-defaults；
  - override 不补全默认项。
- 不可热重载字段：
  - 不采用候选文件值；
  - 生成“恢复首次快照”候选值；
  - 类级标记覆盖模块全部配置字段。
- 不可热重载字段的候选 YAML 值仍是管理员为下次重启提供的持久化值；它不会在本次 reload 应用到运行字段。只有在 global、world-defaults 与所有已加载 world override 均成功解析、绑定和验证后，才允许保存 global/defaults 候选文件。
- 初始加载仅在所有 binder/callback 阶段成功后 materialize migration：将实际采用的旧值写入 target `new-path`，并删除 source `old-path`；新旧路径同时存在时保留新值并删除旧路径。
- 运行时仍校验 enum 字符串和支持类型；模块结构校验已移至测试。

## 与现有代码交互

- `LeafConfig.reloadAsync()` 收集 `ReloadCandidateValue`，不直接生成 applied pending values。
- `LeafConfig` 的 commit transaction 在锁内捕获旧值。
- `LeafWorldConfig` 仍通过独立模块对象获得 defaults merge。

## 测试

- 删除 global option 后重载：字段和写回 YAML 都使用重载前值。
- 删除 world-default option 后重载：defaults 和未覆盖的世界值保留重载前 defaults 值。
- 删除 world override option 后重载：该世界改为继承 defaults。
- 字段级和类级 `@HotReloadUnsupported` 均保持首次成功初始化值。
- 修改不可热重载项后 reload：YAML 保存新值以供下一次重启使用，但本次运行字段仍为首次成功初始化值。
- callback 或 world bind 失败后不产生首次快照；下次成功初始化才建立快照。

## 可能的失败模式

- 继续使用代码默认值会覆盖管理员已运行的配置。
- 在候选收集时捕获旧值会让后续并发/排队 reload 出现 ABA restore。
- 未复制列表会使 defaults、世界实例和快照共享可变值。
