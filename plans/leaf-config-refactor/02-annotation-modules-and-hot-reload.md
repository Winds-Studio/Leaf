# 2. 完成注解式模块绑定与不可热重载策略

## 修改文件

继续完成并审查当前待转换模块：

- `leaf-server/src/main/java/org/dreeam/leaf/config/modules/opt/FastRNG.java`
- `MutableBlockPos.java`
- `OptimizeBiome.java`
- `OptimizeBlockEntities.java`
- `OptimizeDespawn.java`
- `OptimizeEndSurfaceGen.java`
- `OptimizeEntityActivation.java`
- `OptimizeItemTicking.java`
- `OptimizeMobSpawning.java`
- `OptimizeNoActionTime.java`
- `OptimizePlayerMovementProcessing.java`
- `OptimizeRandomTick.java`
- `OptimizeWaypoint.java`
- `OptimizedPoweredRails.java`
- `ReduceUselessPackets.java`
- `SkipInactiveEntityForExecute.java`
- `SkipMapItemDataUpdates.java`
- `SleepingBlockEntity.java`
- `ThrottleNaturalMobSpawning.java`
- `TileEntitySnapshotCreation.java`
- `VirtualThreadSupport.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/modules/opt/global/ReducedIntervals.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/modules/async/SparklyPaperParallelWorldTicking.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/annotations/HotReloadUnsupported.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/annotations/DoNotLoad.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/ConfigModule.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/WorldConfigModule.java`
- 新增 `leaf-server/src/main/java/org/dreeam/leaf/config/migration/ConfigMigrationContext.java`

## 为什么修改

全局模块必须统一交给 binder 管理；异步、线程池、并行 world ticking、region format 等启动期选项不能在运行中变更。

## 具体修改

- 每个配置模块使用 `@ConfigClassInfo` 和 `@ConfigInfo`，删除手工 `basePath()`、`globalConfig.get*()` 和手工 comment 写入逻辑。
- config module 可在自身类中声明 `public static void migrate(ConfigMigrationContext migrations)`。例如 `OptimizeNonFlushPacketSending` 在 `migrations.isBefore("3.1")` 时登记 `old-path -> new-path`。该静态方法通过 `ConfigMigrationContext` 登记 global/defaults/override 的受版本保护迁移；context 是 migration engine，不集中保存 Leaf option path。迁移方法只能登记 operation，不得读取已绑定字段、直接加载或写入目标文件。
- 保持既有用户路径、默认值、英文/中文注释不变。
- 保留模块真正的运行时后处理，例如：
  - `FastRNG` 的 JVM 能力检测和 worldgen 派生开关；
  - `ReducedIntervals` 对 `ServerPlayer.increaseTimeStatisticsInterval` 的赋值；
  - `PluginLibraryLoader` 对 `JavaPluginLoader` 日志开关的同步。
- `FastRNG.worldgen` 保持 `@DoNotLoad`，不写入 YAML。
- 全部 `modules.async` 配置模块改为类级 `@HotReloadUnsupported`：
  - `AsyncChunkSend`
  - `AsyncMobSpawning`
  - `AsyncPathfinding`
  - `AsyncPlayerDataSave`
  - `MultithreadedTracker`
  - `SparklyPaperParallelWorldTicking`
- 保持其他启动期模块的类级不可热重载标记：
  - `RegionFormatConfig`
  - `SleepingBlockEntity`
- 保留非 async 但仅个别字段不可安全重载时的字段级标记，例如 `FastRNG`、`DynamicActivationofBrain`、`EntityGoal`、`ThrottleNaturalMobSpawning`、`SentryDSN`、`VanillaUsernameCheck`。
- `@HotReloadUnsupported` 文档明确：
  - 字段级：重载恢复首次成功初始化后的值；
  - 类级：类内全部 config fields 恢复首次值，且跳过两个回调；
  - `@DoNotLoad` 不参与读取、保存、快照或重载。

## 与现有代码交互

- `LeafConfig.runGlobalModuleCallbacks()` 和 `runAfterBootstrapCallbacks()` 使用类级标记。
- `ConfigBinder` 使用字段级和类级标记建立 reload transaction。
- `LeafConfig` 在 raw global/defaults 或 raw world override 解析后、任何 binder/default 注入前反射调用相应 module class 的静态 `migrate(ConfigMigrationContext)`；没有该方法的 module 不参与 path migration。
- 现有 Minecraft/Paper 调用点继续直接读取原有 static 字段，无额外间接层。

## 测试

- `ConfigModuleContractTest` 断言全部 async module 为类级 `@HotReloadUnsupported`。
- 针对 `FastRNG`、`RegionFormatConfig` 和一个 async module 建立 binder/reload 单元测试，确认派生字段不写 YAML、不可热重载字段不接受新值。

## 可能的失败模式

- 漏标 async 模块会导致线程池、异步任务或 parallel ticking 在运行时被改写。
- 把 `@DoNotLoad` 字段错误配置化会将运行状态持久化。
- 误删 `onLoaded()` 的副作用会改变启动期行为。
