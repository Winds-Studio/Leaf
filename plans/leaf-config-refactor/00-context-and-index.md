# Leaf 配置重构：完整修订实施计划

本目录将原始计划按章节拆分。各子计划中的“实现状态”均以当前 applied source 核对结果为准；“待处理问题”汇总在本文件末尾，并在对应子计划中引用编号。

## 当前实现概况（按源码核对）

已实现：

- `ConfigBinder.validateField()` 已删除；仅保留运行时必需检查（缺失 `@ConfigClassInfo`、null 默认值、不支持类型、非法 enum）。
- reload 串行化：`AtomicBoolean RELOAD_QUEUED_OR_RUNNING` + `ReentrantLock RELOAD_TRANSACTION_LOCK`，任务通过 `CompletableFuture.runAsync(..., server)` 交给 `MinecraftServer` executor（从服务器线程的非任务上下文调用时会同步执行）。将改为与 Paper 一致的同步 reload，见下方决定。
- reload 缺失键回退：global 使用当前 static 字段值，world defaults 使用 active defaults module 值；world override 缺失键继承 candidate defaults。
- 不可热重载：字段级/类级 `@HotReloadUnsupported`，首次成功初始化快照；reload 恢复快照值，类级跳过两个回调。
- 启动致命：`loadConfig()` / `loadAfterBootstrap()` 失败抛 `IllegalStateException`，由 `Main` 最外层 `catch (Throwable)` 终止启动。
- 延迟显式保存：global/defaults 在 `loadAfterBootstrap()` 末按 defaults → global 保存；读取缺失目标仍可能提前建空文件（P1-9）。
- Paper 风格保存：`ConfigFileIO.save()` 逐文件尝试，`AccessDeniedException` 记 warning 并沿用内存配置，其他失败在全部尝试后抛 `ConfigSaveException`，附 `SAVED/ACCESS_DENIED/FAILED` 报告；无跨文件回滚。
- 共享 backup session：`config/backup-<yyMMdd-HHmmss>[-N]`，延迟创建。
- Leaf path migration：module 静态 `migrate(ConfigMigrationContext)` 登记，作用于 raw 内存 `ConfigFile`，在任何 binder 读取/默认值注入前执行。
- Gale 迁移按文件分阶段；Leaf 目标权限拒绝时保留 Gale 源文件（`recordLeafPersistence` / `RETAINED_GALE_SOURCES`）。
- Gale 配置类已删除。

未完成：

- Gale runtime 引用仍残留（见子计划 09），当前源码树无法编译。
- `LocatorBarRange`、`DensityFunctionCompiler` 仍是旧式模块（`extends ConfigModule` + `basePath()`），与接口化的 `ConfigModule` 不兼容（见子计划 02）。
- 测试文件尚未创建（见子计划 10）。

## 启动与世界加载顺序（当前源码）

| 阶段 | 位置 | 内容 |
|---|---|---|
| ① | `Main` → `LeafConfig.loadConfig()` | 创建 backup session → `purgeOutdated()` → 创建 `config/` → raw load `leaf-global.yml` / `leaf-world-defaults.yml` → 发现 modules → Leaf path migration（用 raw global 的 `config-version` 判断）→ Gale global/defaults migration → `new LeafGlobalConfig(..., true)`：把旧版本存入内存 `previousConfigVersion`，并在内存中设为当前版本 → 绑定 global → `onLoaded()` → 绑定 world defaults → 记录 defaults 快照 |
| ② | `Bootstrap.bootStrap()` | — |
| ③ | `Main` → `LeafConfig.loadAfterBootstrap()` | `onRegistriesLoaded()` → 记录 global 快照 → 保存 defaults、global（磁盘版本号此时变为当前版本）→ `recordLeafPersistence` |
| ④ | `ServerLevel` 构造 → `LeafConfig.initWorldConfig()` | 有 `gale-world.yml` 且无 `leaf-world.yml`：Gale world 迁移；否则：raw load override → Leaf override rename（用内存 `previousConfigVersion` 判断）→ 绑定 → 迁移有变更时保存 → 记录世界快照 |
| ⑤ | `MinecraftServer` → `GaleConfigMigration.finalizeMigration()` | 启动期已加载世界全部完成后、`POSTWORLD` 插件启用前，归档 Gale 文件 |
| ⑥ | 运行期 `createWorld` | 再次执行 ④；不会执行 ⑤ |

## P0-1 版本号与逐世界迁移（已决定，待实施）

- 只维护一套 `CURRENT_CONFIG_VERSION`。`leaf-global.yml` 的 `config-version` 记录 global/defaults 的进度；每份已有 `leaf-world.yml` 的 `config-version` 只记录该 override 的进度。
- 版本按数字逐段比较（`3.10 > 3.9`）。override 不回退到 global 的版本。
- **版本号规则（P1-11、P2-8，与 Paper 一致）**，global 与已有 override 相同：
  - 文件存在但缺少 `config-version`：WARN（含文件路径），视为当前版本，不执行迁移，原有内容按当前格式的合法配置读取；视为需要写回，保存时补写当前版本，此后不再警告。override 原本只在迁移时保存，缺版本是其例外：只补写版本，保留原有显式键，不混入 defaults（P1-8 的保存边界同样适用）。新建文件（文件原本不存在）直接写当前版本，不警告。
  - 无法解析：拒绝加载并报文件路径与原文本。global 为启动致命、reload 失败；override 按 P1-5 使本次世界加载失败。
  - 高于 `CURRENT_CONFIG_VERSION`：按 P2-2 WARN，不执行迁移，保留文件中的原版本，不改写为当前版本。
  - 低于当前版本：按版本执行迁移，保存时写入当前版本。
  - 依据：已发布的 Leaf（如 1.21.11 分支）global 已写入 `config-version: 3.0`，缺版本不再代表需要迁移的旧配置。
- 仅有实际的非版本 override 值时才新建文件，不为记录版本而建文件。
- global/defaults 仍在 ③ 保存；启动时不扫描世界。④ 在世界加载、绑定配置前按该文件的版本迁移，绑定成功后将内容和版本一起保存。插件跨重启晚加载也走同一路径。实施细节见 [06 世界配置生命周期](06-world-config-lifecycle.md) 和 [08 迁移阶段](08-migration-stages.md)。

## P2-2 配置降级警告（已决定，待实施）

读取 `leaf-global.yml` 或已有 `leaf-world.yml` 时，若文件版本高于 `CURRENT_CONFIG_VERSION`，记录 WARN，列出文件路径、文件版本和当前支持版本。同时不执行迁移，并保留文件中的原版本，不改写为当前版本（P2-8）。见 04、08。

## 冲突处理决策

- “初始化失败停止服务器”适用于 Leaf raw 解析、Leaf path migration、binder、模块回调、最终保存（非 `AccessDeniedException`）。legacy purge 失败只警告（P2-10）。
- Gale migration 中，需导入的非 null 映射值若无法按旧 Gale 字段类型读取，按 Leaf 迁移错误使本次启动或世界加载失败；普通 YAML null 按缺键跳过。同源不得映射到不同目标，由定义测试保证（见 08）。其他 Gale 单阶段失败仍可恢复：该阶段不保留部分值，记录错误，继续其他阶段与正常 Leaf 启动；未产生待保存值的源文件沿用原归档策略；有值需持久化的先确认对应目标保存成功，再归档。
- Leaf 配置保存采用 Paper 风格：候选全部成功后才逐文件保存；`AccessDeniedException` 仅 warning 并使用内存配置；不创建 staged/rollback 文件。
- reload 与 Paper 一致在调用线程同步执行，不排队、不回滚。

## 已决定，待实施：reload 与配置迁移

- **P0-2 reload 按文件版本重做未落盘的 Leaf 迁移。**
  - Paper 的设计：启动和 `reloadConfigs()` 走同一条路径——读取文件 → 按文件自身 `_version` 执行版本迁移 → `trySaveFileNode()`（权限拒绝只警告）。global、world defaults 和每个世界文件各有自己的 `_version`。保存失败时磁盘版本仍是旧值，reload 会自动重新迁移。
  - Paper 考虑到的部分（需处理）：版本迁移未落盘时，reload 不应丢失。reload 在内存中按候选 global 的版本迁移 global/defaults，仅遍历已加载世界，按每份候选 override 自身版本迁移。全部候选成功后在提交阶段保存；单个 override 权限拒绝时其磁盘进度不变，不阻止 global 记录 global/defaults 的进度。
  - 已接受的边界：reload 不重放 Gale 转换。若权限拒绝导致新 override 只存在于内存，reload 后该世界使用 defaults；下次加载世界时从保留的 Gale 源重试。
  - 见 06、07。
- **P0-3 Gale 只补缺失的 Leaf 值。** global/defaults 在注入默认值前检查 raw Leaf 目标路径，已有值就保留；同值不警告，异值记录冲突。世界已有 Leaf override 时仍整份优先，不按键混入 Gale。见 08。

- **P1-1 运行期加载的世界不会归档 `gale-world.yml`。** `finalizeMigration()` 只在启动期执行一次。插件在 `POSTWORLD` 或之后创建的世界迁移后，Gale 源文件残留，此后每次启动都会 warning“Leaf override 已存在”。待将归档拆到各自阶段：③ 保存后处理 Gale global/defaults，每次 ④ 处理该世界的 Gale 源，共用同一 backup session；删除 ⑤ 的全局遍历。见 05、08。
- **P1-2 reload 回调遇错即停，最后保存。** 候选通过后统一应用字段；回调失败不再保存。最终保存遇到权限拒绝只警告，其他异常报 ERROR 和堆栈，不报成功，不回滚。见 07。
- **P1-3 快照随配置实例存活。** world defaults 和各世界的 `LeafWorldConfig` 分别按需持有自己的首次值；普通 reload 不替换快照，世界卸载再加载则重新建立。global 保留服务器级快照；不新增卸载回调。见 03。
- **P1-4 Leaf 路径迁移由定义测试把关。** 同源多目标、跨操作父子重叠由测试检查；链式顺序由作者保证，用保存重读测试确认。不新增运行时规则校验或排序，旧键显式 null 仍是非法输入。新旧值冲突时保留新值、删除旧键，仅异值记录 WARN，并注释优先保留新值的原因。与 Gale 导入无关。见 08、10。
- **P1-7 FastRNG 仅 worldgen 不支持热重载。** 保留首次启动能力检测后的世界生成算法，主开关和 slime chunk 选项仍可热重载；不锁整个模块。见 02。
- **P1-8 迁移已写入新键，但随后绑定时可能被默认值覆盖。** 保持先迁移、后绑定；在绑定层使默认值只填真正缺失的键。不新增运行时校验，由测试覆盖 Leaf rename 与 Gale global/defaults。见 08。
- **P1-5 world override 加载失败与 Paper 一致，保持现状。** 解析、迁移、绑定或非权限保存失败从 `ServerLevel` 构造抛出：启动期致命，运行期 `createWorld` 失败；权限拒绝仅警告。不加回退或清理。见 06。
- **P1-6 旧式模块转为新格式（预期 WIP）。** `LocatorBarRange`、`DensityFunctionCompiler` 尚未转换、暂无法编译，属于当前进度；按 02 转换，路径与注释不变。同属此项：Gale 映射模块换包后（`network.global.*` → `network.*`、`gameplay.global.BookWriting` → `gameplay.BookWriting`），`SignedMessageChain`、`ServerCommonPacketListenerImpl`、`ServerLoginPacketListenerImpl`、`ServerGamePacketListenerImpl` 中的旧类名引用一并更新。
- **P1-6 `ThrottleNaturalMobSpawning` 保持手工读取。** 顺序问题由 P1-2 解决；reload 删除键回到代码默认值作为已接受例外。见 02。
- **P1-6 直接移除废弃键 `run-async-tasks-sync` 的处理。** 删除字段、读取与警告，不转换为 `BUFFERED`；暂不扩展值转换迁移。见 02。
- **P1-9 缺失文件不再在读取时创建。** 仅确认文件不存在时只在内存构建，整个加载成功并保存时才创建；目录、失效软链接或权限错误如实报错。覆盖启动、reload 及 Gale 世界转换重试。见 04、07、08。
- **P2-4 `ConfigBackupSession.move()` 改为 `void`。** 它只会成功或抛 `IOException`，没有返回 `false` 的情况；失败处理不变。不考虑 `config/` 位于其他盘符或文件系统。见 05。
- **P1-10 并行读策略消费端转为派生枚举（预期 WIP）。** 两个 CraftBlock 分支及 ServerLevel 队列分支改读 `asyncUnsafeReadHandlingPolicy`，YAML 输入不变。见 02。
- **去除异步 reload。** `reloadAsync()` 改为同步 `reload(sender)`，删除 future、并发标记和锁，不加状态门槛。原 P2-5、P2-6 随之消失。见 07。
- **P2-7 拒绝存在但不可解析的标量。** boolean、int、long、double 解析失败时报文件、path、期望类型：启动致命、reload 失败不提交、世界加载失败。缺键和 null 仍回退；引号数字仍接受。见 03。
- **P2-13 开发者定义错误移至测试，删除运行时检查。** 下列检查只在代码写错时触发，改由契约或定义测试保证（见 01、08、10）；用户数据校验（迁移旧键为 null 或配置节、目标为配置节、非法 enum、P2-7）保留：
  - `ConfigBinder.registerGlobalDefaults()`、`bindWorld()` 的 null 默认值检查（01 已列入测试，源码有 `TODO: move it to test`）；
  - `bind()`、`collectGlobalReload()` 重复检查 `@ConfigClassInfo`，`ConfigPaths.modulePath()` 已会抛错；
  - `collectWorldReload()`、`applyWorldDefaults()` 的模块类型相等检查：两侧取自同一 `Field`，类型必然相同；
  - `LeafConfigMigration.validate()` 与目标查重（路径非空、同作用域同路径、单条规则父子重叠、不同来源同目标），以及 `collect()` 对 `migrate` 签名的检查。P1-4 决定保留这些，与“定义问题放测试”不一致。数据校验（旧键为 null 或配置节、目标为配置节）保留；
  - `GaleConfigMigration.resolveMappings()` 找不到目标字段时，`migrateStage()` 记 error 并跳过整个阶段，把开发者错误变成运行时不迁移；改由 Gale 定义测试保证，运行时直接抛出；
  - `ConfigFileIO.save()` 拒绝同一文件保存两次：调用方固定传 defaults 与 global；
  - `initialReloadValue()` 快照缺失时抛错：只在启动完成前 reload 才会发生，属不支持的调用。
- **P2-11 直接删除 `Server.Spigot#getGaleConfig()`。** 不保证 Gale 配置 API 兼容性，不保留过渡实现。未映射 Gale 选项的去向在实施时核对，见 09。
- **P2-16 不加 Gale 残留引用的源码扫描测试。** 配置类已删除，残留引用会编译失败。见 09。
- **P1-11 缺版本按 Paper 处理（含原 P1-14）。** 文件存在但缺少 `config-version` 时 WARN、视为当前版本、不迁移，并写回一次补上版本，避免重复警告；override 也写回。无法解析则拒绝加载。适用于 global 与 override，见上方“版本号规则”。override 由用户定义，Leaf 默认不生成（仅 Gale 转换会建立，且首次保存即带版本）；用户已有、缺少版本的 override（含本分支此前生成的无版本文件）补写当前版本并更新到文件，写回只含原有显式键与版本（P1-8 边界）。
- **P2-8 降级时保留更高的版本号。** 文件版本高于当前支持版本时只 WARN、不迁移、不改写版本。见上方“版本号规则”。
- **P1-12 `createDirectory()` 不再删除同名文件。** 去掉对 `FileAlreadyExistsException` 的 catch 与 `dir.delete()`，异常直接抛出：启动失败、reload 失败。与 Paper `createDirectoriesSymlinkAware()` 一致。
- **P2-12 保留为尚不存在的迁移所做的设计。** 04 defaults 保存被拒时 global 版本不前进、08 跨文件迁移的权限拒绝处理、06 `WORLD_DEFAULTS → GLOBAL` 后 override 旧键 warning，均按原计划保留。
- **P2-14 删除无用的回滚与重复类型。** 删除 `LeafConfigMigration.apply()` 的快照恢复（失败时调用方整体丢弃配置）；删除 Gale `applyMappings()` 的原值恢复，按 08 先收集校验再写入；只保留 `PendingValue`，删除 `ReloadValue` 与 `preparePendingValues()`，构造时复制一次。现有设计下字段不会只应用一半新值；跨文件保存可能部分成功，属已接受的 Paper 风格。见 03、07、08。
- **P2-15 清理无用代码。** 删除 `ConfigBinder.GLOBAL_DEFAULT_VALUES`、`registerGlobalDefaults()`、`globalDefaultValue()`，启动期 `bindGlobal()` 与 world 一样以 `field.get(null)` 作为默认值：global 只在启动绑定一次，此前的 Leaf/Gale 迁移只改 raw `ConfigFile`、不读写字段，字段仍是代码默认值。删除 `discoverWorldModules()` 的 `isEmpty()` 早退。`loadAfterBootstrap()` 的 catch 不再记录日志，只包装抛出，由 `Main` 记录。`LeafConfigAccessor` 的辅助方法与 `isConfigVersionAtLeast()` 保留。见 03。
- **P2-15 随 P0-1 删除旧版本辅助代码。** 删除 `previousConfigVersion`、`loadPreviousConfigVersion()`、单参数 `isConfigVersionBefore(String)`、`ConfigMigrationContext(null)` 的回退分支，以及 `LeafGlobalConfig` 的 `loadPreviousVersion` 参数。`isConfigVersionAtLeast()` 保留。见 06、08。
- **P2-3 非原子保存与 Paper 保持一致。** 直接写目标文件，不改为临时文件加原子移动；写入中途失败留下的半截 YAML 下次启动按 malformed 处理。见 04。
- **P2-9 拒绝结构不符的值。** 标量与 `String`、enum 字段遇到列表或配置节、`List` 字段遇到非列表，均拒绝；enum 分支的报错提取为帮助方法，补上文件路径，供 enum、P2-7、P2-9 共用。见 03。
- **P2-10 purge 失败改为警告。** purge 只移动 legacy 文件，不转换内容；失败时文件留在原处，下次启动再试，结果相同。任一移动失败都 WARN 并继续启动。见 04、05。
- **P2-17 降级时丢失新版本新增的键是预期行为。** 旧版 Leaf 保存时只写自己认识的键，不保留较新版本新增的键；不为此跳过保存。
- **P2-18 Gale 文件中未映射的键给 WARN。** 每个阶段列出存在但未映射、且不是 `_version` 的键，说明不再支持、不会迁移。见 08。
- **P2-20 Spark 世界配置登记保持现状。** 已核对：内置 spark 的 `LeafServerConfigProvider` 在每次生成元数据时，用 `leaf/` 分组按 `Bukkit.getWorlds()` 重新列出全部世界的 `leaf-world.yml`，晚加载的世界也包含在内，不依赖系统属性。外部 spark 插件（`BukkitServerConfigProvider`）只在类首次初始化时（第一次生成元数据）读一次 `spark.serverconfigs.extra`，之后不再读取，所以在世界加载后重新登记无效。spark 跳过不存在的文件（`ConfigParser.parse()` 先检查 `Files.exists`），登记不存在的 override 无害。
- **P2-19 删除各世界的 `WorldReload` 与 `setConfigFile()`。** 它们是 `b8504490` 可回滚 reload 的残留（`845e6cae` 已删除回滚本身），提交后换入的 `ConfigFile` 无人读取。保留 world defaults 的 `setConfigFile()`（提交时据此保存）；P0-2 的 override 保存直接使用候选 `ConfigFile`。见 07。
- **P2-1 Gale 无映射命中不建 override。** 仅含 `_version` 或全是未映射键时，不生成 `leaf-world.yml`。见 08。
- **P1-13 SecureSeed 改回 global。** 世界作用域是先前的实验性改动，撤回：恢复 global 模块 `modules/misc/SecureSeed`，`misc.secure-seed.enabled` 仍在 `leaf-global.yml`，路径与注释不变，与 ver/26.2 一致，无需迁移。删除 `modules/misc/world/SecureSeed`、`LeafWorldConfig.secureSeed` 与 `SecureSeed.isEnabled()`，`Globals.setupGlobals()` 改回读 global；Minecraft/Paper 源码中 16 处对 `modules.misc.SecureSeed.enabled` 的引用因此恢复有效。`enabled` 加 `@HotReloadUnsupported`：它影响世界生成，与 P1-7 同类，reload 恢复首次启动的值，新值写入 YAML 供下次启动使用。
- **P2-22 world module 可以使用 `@HotReloadUnsupported`（设计所需）。** 保留 P1-3 的按实例快照；在 `HotReloadUnsupported` 的 Javadoc 中说明：同样适用于 world module，world defaults 与每个世界各自保留首次成功绑定的值，世界卸载后重新加载时重新建立。见 02、03。
- **P2-21 现有 3.1 迁移声明作为样例保留；只在登记了迁移时升版。** `OptimizeBlockEntities`、`OptimizeRandomTick` 的两条声明在 Leaf 生成的文件上实际不会触发（旧键早已被旧代码删除），先保留，作为迁移框架的示例与测试输入。`CURRENT_CONFIG_VERSION` 只在新登记迁移时递增；新增选项由默认值填充，不升版。这样可避免仅为新增项就重写所有版本落后的 override（P0-1），也避免降级时无意义的 P2-2 警告。见 08。
- **P2-23 `Level.leafConfig` 改为 `private final`。** 删除无调用方的 `setLeafConfig()` 与 `volatile`；reload 原地写字段、不替换实例，与 Paper `paperConfig` 一致。字段值的跨线程可见性仍按 07 的风险说明。
- **P2-25 合并绑定路径。** `bind()`（含 `global` 布尔分派）、`bindWorld()`、`bindWorldDefaultsForReload()`、`collectGlobalReload()` 合并为一个“读取模块 → `PendingValue` 列表”的函数，参数为 fallback 来源及是否写默认值与注释；启动期也先收集再 `apply()`。见 03。
- **P1-16 回调不得改写 `@ConfigInfo` 字段，派生值放进 `@DoNotLoad` 字段。** 把配置值 A 算出的 B 直接写回配置字段，是旧式模块的写法。它与 reload“缺键沿用当前字段值并写回”及首次快照冲突：删键后 reload 会把派生值写进 YAML（如 FastRNG `enabled: false`、并行世界 `threads: 0`），快照也记录回调后的值。一律改为配置字段只保存读入值，回调把规范化或派生结果写入 `@DoNotLoad` 字段，运行期读取方改读派生字段（`AsyncPathfinding.rejectPolicyType`、`SparklyPaperParallelWorldTicking.asyncUnsafeReadHandlingPolicy` 已是此写法）。涉及：`FastRNG.enabled`、`SparklyPaperParallelWorldTicking.threads/logContainerCreationStacktraces/disableHardThrow`、`AsyncPathfinding.maxThreads/queueSize`、`MultithreadedTracker.threads`、`IceAndSnowChance.iceAndSnowChance`、`ConnectionMessage.joinMessage/quitMessage`、`RegionFormatConfig.compressionLevel/ioFlushDelay`、`MCBugFix.mc301114maxCombatEntries`、`ProtocolSupport.doABarrelRollInstalledTimeout`、`VanillaUsernameCheck.removeAllCheck`。见 02。
- **P2-30 删除复活的 `flush-location-while-knockback-player`。** ver/26.2 的 `65fe1ee4` 已删除 `Knockback.flushKnockback` 及该配置项（由 `use-legacy-tracker-ticking` 取代），注解式重写又将其加回，且无读取点。删除该字段，与 ver/26.2 一致。由 `tools/config_paths.py` 对比 ver/26.2 发现。
- **MC/Paper 源码中的配置改动并入 Leaf config changes 补丁。** Minecraft 源码并入 `leaf-server/minecraft-patches/features/0333-Leaf-config-changes.patch`（applied 仓库提交 `Leaf config changes`），paper-server 并入 `leaf-server/paper-patches/features/0069-Leaf-config-changes.patch`。paper-api 新建 Leaf config changes 补丁（`leaf-api/paper-patches/features/`），收录 P2-11 等 API 侧改动。全部阶段完成后由所有者统一转换为补丁，供整体 review。见 11。
- **不处理：** 未登记的键在保存时被静默删除（原 P2-29），属用户配置问题，不为此告警。未加引号、被 YAML 读成浮点数的版本号（原 P2-28）属非法格式，按“版本号规则”处理。

## 待处理问题（再审查结论）

### P1：设计不当或边界行为不明确

（暂无）

### P2：次要边界

（暂无）

## 实施记录

按 11 的阶段记录每轮完成情况、偏差与验证结果。

- **实施前基线（`config_paths.py` 对比 ver/26.2）：** ver/26.2 的选项在工作区全部存在；`scope changed` 仅 `misc.secure-seed.enabled`（见 P1-13）；`only in working tree` 为 Gale 映射引入的选项与 P2-30。`default changed` 的 4 项已核对等价：`async-pathfinding.reject-policy`（旧版将运行期算出的策略写入 YAML，新版写 `default` 并在运行期计算，已有显式值不受影响）、`async-unsafe-read-handling`（同为 `DISABLED`）、`disable-world-data-saving.worlds`（同为空列表）、`do-a-barrel-roll-installed-timeout`（旧版 YAML 默认 0，运行期 ≤0 改为 40；新版默认 40，运行效果相同）。

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
11. [实施阶段与流程](11-implementation-phases.md)（路径对比工具：`tools/config_paths.py`）
