# 2. 完成注解式模块绑定与不可热重载策略

## 修改文件

- `leaf-server/src/main/java/org/dreeam/leaf/config/modules/**`（尚未转换的模块，见“实现状态”）
- `leaf-server/src/main/java/org/dreeam/leaf/config/annotations/{HotReloadUnsupported,DoNotLoad}.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/{ConfigModule,WorldConfigModule}.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/migration/ConfigMigrationContext.java`
- `paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlock.java`、`leaf-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java`（并行读策略的运行期消费者）

## 为什么修改

全局模块必须统一交给 binder 管理；异步、线程池、并行 world ticking、region format 等启动期选项不能在运行中变更。

## 实现状态

已完成：

- `ConfigModule` 为接口，提供 `onLoaded()` 与 `onRegistriesLoaded()` 默认空实现；`WorldConfigModule` 为标记接口。
- 大部分模块已改为 `@ConfigClassInfo` + `@ConfigInfo`。
- `modules.async` 下现存的全部模块已有类级 `@HotReloadUnsupported`：`AsyncMobSpawning`、`AsyncPathfinding`、`AsyncPlayerDataSave`、`MultithreadedTracker`、`SparklyPaperParallelWorldTicking`（原计划中的 `AsyncChunkSend` 不存在）。
- 其他类级：`RegionFormatConfig`、`SleepingBlockEntity`。
- 字段级：`FastRNG`、`DynamicActivationofBrain`、`EntityGoal`、`ThrottleNaturalMobSpawning`、`SentryDSN`、`VanillaUsernameCheck`、`DensityFunctionCompiler`。
- 当前没有任何 world module 使用 `@HotReloadUnsupported`。
- migration 声明已实现：`OptimizeBlockEntities`、`OptimizeRandomTick` 在 `isBefore("3.1")` 时登记 `GLOBAL → GLOBAL` rename。

未完成（P1-6）：

- `LocatorBarRange`、`DensityFunctionCompiler` 仍是旧式写法（`extends ConfigModule`、`basePath()`、`globalConfig` 字段），与接口化的 `ConfigModule` 不兼容，无法编译。这是预期的 WIP 进度，不是待决策问题；已决定转换为新格式：
  - `LocatorBarRange`：`@ConfigClassInfo(category = GAMEPLAY, name = "")`（或等价路径）+ `@ConfigInfo(name = "locator-bar-max-distance")`，保持路径 `gameplay-mechanics.locator-bar-max-distance` 与注释不变。
  - `DensityFunctionCompiler`：`enabled` 用 `@ConfigInfo` + 字段级 `@HotReloadUnsupported` 表达，删除 `densityFunctionCompilerInitialized` 手工“仅首次读取”逻辑；路径与注释不变。
- 手工读取的 key：
  - `ThrottleNaturalMobSpawning`（已决定：保持手工读取）：按 `MobCategory` 动态生成的 `min-failed` / `spawn-chance` 无法用固定字段注解表达，保留在 `onLoaded()` 手工读取。顺序问题由已决定的 P1-2（回调后再保存）解决；其余差异作为已接受的例外记录，不扩展 binder。需要接受并记录：这些 key 在 reload 中缺失时回到代码默认值（不适用 03 的“沿用运行值”规则）；当前 reload 在保存后运行 `onLoaded()`；rc.3 只序列化已登记的输出键，这还可能漏写磁盘上原有但尚未由回调登记的自定义类别值。P1-2 调整先后顺序后，回调登记的键可随本次保存写入，但删除键后回退到 `8` / `25.0` 的例外不会因此改变。
  - `SparklyPaperParallelWorldTicking` 的废弃 key `run-async-tasks-sync`（已决定：直接移除）：删除 `runAsyncTasksSync` 字段、`onLoaded()` 中的读取和警告及 `TODO`，无其他引用。不将旧 `true` 转换为 `BUFFERED`，也暂不扩展 `ConfigMigrationContext` 的值转换；待以后有其他实例再设计。实施前确认不再登记的旧键在保存后从 YAML 中消失。

### P1-7 FastRNG 派生的世界生成算法仍能在 reload 时改变

**归因：Leaf 引入** — Leaf 的 Faster RNG 补丁（Minecraft 0121、Paper 0037）在原版随机源和 `FasterRandomSource` 之间选择；Paper 基线没有该开关。锁定 `enableForWorldgen` 没有锁住 `FastRNG.onLoaded()` 算出的 `worldgen = enableForWorldgen && enabled`。

启动时两项均开启且 JVM 支持算法，reload 只关闭 `enabled`：`StructurePlacement` 等后续调用改选 legacy，而已经初始化的 `Biome.TEMPERATURE_NOISE` 等不会重建。已决定：仅 worldgen 不支持热重载。保留 `enableForWorldgen` 的字段级 `@HotReloadUnsupported`；派生字段 `worldgen` 仅在首次成功初始化、JVM 能力检测完成后赋值，reload 回调不得再次赋值。它有 `@DoNotLoad`，不进入 binder 快照，单给它加注解不能阻止回调改写。主开关 `enabled` 和两个 slime chunk 选项仍可热重载，不标记整个模块。

### P1-10 并行读策略的三个消费点仍比较字符串和枚举（预期 WIP，已决定转换）

与 P1-6 相同，属于当前进度，不是待决策问题。

**归因：Leaf 引入** — Paper 基线没有此分支；Leaf 并行世界补丁（Minecraft 0188、Paper 0047）接入读策略。当前模块将 YAML 字符串解析为 `asyncUnsafeReadHandlingPolicy`，但 `CraftBlock.java:89,94`、`ServerLevel.java:905` 仍用 `asyncUnsafeReadHandling` 与 `UnsafeReadPolicy` 做 `==` / `!=`，是 Java 静态类型错误，不是运行时恒为 false。

保留 YAML 输入及 `fromString` 转换；两个 CraftBlock 分支和世界读队列分支都改读派生枚举，分别核对 BUFFERED、STRICT 和 DISABLED。不需要改变线程检查或排队行为。

### P1-13 SecureSeed 改回 global（已决定，已实施）

世界作用域是先前的实验性改动，撤回：恢复 global 模块 `modules/misc/SecureSeed`（`@ConfigClassInfo` + `@ConfigInfo`），`misc.secure-seed.enabled` 留在 `leaf-global.yml`，路径与注释同 ver/26.2，无需迁移。删除 `modules/misc/world/SecureSeed`、`LeafWorldConfig.secureSeed`、`SecureSeed.isEnabled()`；`su.plo.matter.Globals.setupGlobals()` 改回读 `SecureSeed.enabled`。Minecraft/Paper 中现有的 `modules.misc.SecureSeed.enabled` 引用不需要修改。`enabled` 加 `@HotReloadUnsupported`（影响世界生成，与 P1-7 同类）。

## 实现状态（阶段 0、2）

P1-6、P1-10、P1-13 已在阶段 0 完成；P1-7、P1-16、P2-23 已在阶段 2 完成，细节与偏差见 00 的实施记录。以下“未完成”与“P1-7/P1-10/P1-13”小节保留为决策记录。

## 具体修改（规则）

- 每个配置模块使用 `@ConfigClassInfo` 和 `@ConfigInfo`，不写手工 `basePath()`、`globalConfig.get*()` 和 comment 逻辑。动态 key 例外，见上。
- config module 可在自身类中声明 `public static void migrate(ConfigMigrationContext migrations)`，通过：
  - `migrations.isBefore("x.y")` 判断本次迁移上下文的存储版本（global/defaults 取 global，override 取该文件自身版本）；
  - `migrations.migrate(Scope source, String oldPath, Scope target, String newPath)` 登记，`Scope` 为 `GLOBAL` / `WORLD_DEFAULTS`。
  迁移方法只能登记 operation，不得读取已绑定字段、直接加载或写入文件。
- 保持既有用户路径、默认值、英文/中文注释不变。
- 保留模块真正的运行时后处理（`FastRNG` 能力检测与派生开关、`ReducedIntervals` 对 `ServerPlayer.increaseTimeStatisticsInterval` 的赋值、`PluginLibraryLoader` 日志开关同步）。
- `FastRNG.worldgen` 保持 `@DoNotLoad`，不写入 YAML。
- `@HotReloadUnsupported` 语义（已在注解 Javadoc 中写明）：
  - 字段级：reload 恢复首次成功初始化后的值；
  - 类级：全部 config fields 恢复首次值，且 reload 跳过 `onLoaded()` 与 `onRegistriesLoaded()`；
  - world module 同样可用（P2-22，设计所需）：world defaults 与每个世界各自保留首次成功绑定的值，世界卸载后重新加载时重新建立（见 03 P1-3）。待在 `HotReloadUnsupported` 的 Javadoc 中补充这一点；
  - `@DoNotLoad` 字段不参与读取、保存、快照或重载。
- 字段级不可热重载的模块在 reload 时仍会运行回调；回调中依赖该字段的派生状态必须基于恢复后的首次值计算。
- **P1-16（已决定，已实施）回调不得改写 `@ConfigInfo` 字段。** 配置字段只保存读入值；规范化或派生结果写入 `@DoNotLoad` 字段，运行期改读派生字段。“由 A 算出 B 再写回配置字段”是旧式模块写法，会使 reload 缺键回退和首次快照记录到派生值。待调整的模块列表见 00 P1-16。

## 与现有代码交互

- `LeafConfig.runGlobalModuleCallbacks()` 和 `runAfterBootstrapCallbacks()` 在 reload 时跳过类级标记模块。
- `ConfigBinder.collectGlobalReload()` / `collectWorldReload()` 对字段级和类级标记生成“恢复首次快照”的候选值。
- Minecraft/Paper 调用点继续直接读取 static 字段，无额外间接层。

## 测试

- `ConfigModuleContractTest` 断言 `modules.async` 下全部模块为类级 `@HotReloadUnsupported`。
- 针对 `FastRNG`、`RegionFormatConfig`、`DensityFunctionCompiler` 和一个 async module 建立 binder/reload 单元测试，确认派生字段不写 YAML、不可热重载字段不接受新值。

- FastRNG：启动两个开关均为 true，确认能力检测成功；单独关闭主开关并连续 reload，断言 `enabled` 变为 false，但 `worldgenEnabled()` 仍为 true；反向验证首次决策为 false 时，reload 打开主开关也不能开启 worldgen。重启后才按新配置重新选择。
- 刷怪动态键：保留一个自定义类别值、删除另一个，分别断言回调后数组、当次保存的 YAML 和再次加载的值；`spawn-chance` 的文件单位是百分比，不是运行期缩放后的 int。

## 可能的失败模式

- 漏标 async 模块会导致线程池、异步任务或 parallel ticking 在运行时被改写。
- 把 `@DoNotLoad` 字段错误配置化会将运行状态持久化。
- 误删 `onLoaded()` 的副作用会改变启动期行为。
- 转换旧式模块时改变路径或注释会让用户已有配置失效。
