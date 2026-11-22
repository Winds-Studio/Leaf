# Leaf 配置重构：完整修订实施计划

本目录将原始计划按章节拆分。各子计划中的“实现状态”均以当前 applied source 核对结果为准；“待处理问题”汇总在本文件末尾，并在对应子计划中引用编号。

## 当前实现概况（按源码核对）

> 本节描述阶段 0 之前的源码状态，保留作历史对照。实施后的状态见“实施记录”各阶段条目；与本节冲突时以实施记录为准。

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

## P0-1 版本号与逐世界迁移（已决定，已实施）

- 只维护一套 `CURRENT_CONFIG_VERSION`，但**每个文件各自记录**自己的进度：`leaf-global.yml`、`leaf-world-defaults.yml`、每份已有 `leaf-world.yml` 都有自己的 `config-version`（P0-4，取代原“global 的版本同时记录 defaults 进度”）。
- 版本按数字逐段比较（`3.10 > 3.9`）。override 不回退到 global 的版本。
- **版本号规则（P1-11、P2-8，与 Paper 一致）**，global 与已有 override 相同：
  - 文件存在但缺少 `config-version`：WARN（含文件路径），视为当前版本，不执行迁移，原有内容按当前格式的合法配置读取；视为需要写回，保存时补写当前版本，此后不再警告。override 原本只在迁移时保存，缺版本是其例外：只补写版本，保留原有显式键，不混入 defaults（P1-8 的保存边界同样适用）。新建文件（文件原本不存在）直接写当前版本，不警告。
  - 无法解析：拒绝加载并报文件路径与原文本。global 为启动致命、reload 失败；override 按 P1-5 使本次世界加载失败。
  - 高于 `CURRENT_CONFIG_VERSION`：按 P2-2 WARN，不执行迁移，保留文件中的原版本，不改写为当前版本。
  - 低于当前版本：按版本执行迁移，保存时写入当前版本。
  - 依据：已发布的 Leaf（如 1.21.11 分支）global 已写入 `config-version: 3.0`，缺版本不再代表需要迁移的旧配置。
- 仅有实际的非版本 override 值时才新建文件，不为记录版本而建文件。
- global/defaults 仍在 ③ 保存；启动时不扫描世界。④ 在世界加载、绑定配置前按该文件的版本迁移，绑定成功后将内容和版本一起保存。插件跨重启晚加载也走同一路径。实施细节见 [06 世界配置生命周期](06-world-config-lifecycle.md) 和 [08 迁移阶段](08-migration-stages.md)。

## P0-4 `leaf-world-defaults.yml` 有自己的 `config-version`（已决定，已实施）

- 与 global、override 共用 `CURRENT_CONFIG_VERSION` 和同一套版本号规则（缺失 WARN 并视为当前、无法解析拒绝加载、更高只警告且保留版本）；文件不存在时按当前版本新建。
- 动机：只靠 global 的版本记录 defaults 进度，在“只删除/重置 global”“只还原其中一个文件”“defaults 来自更新版本”等情况下进度错位；每个文件各自记录与 Paper 的 `_version` 一致，也使 defaults 能有降级警告。
- 迁移判定：一条迁移由**其来源文件**的版本决定。来源在 global 的操作用 global 的版本判断，来源在 defaults 的用 defaults 的版本判断；执行顺序为先所有来源在 global 的操作，再所有来源在 defaults 的操作（现有声明均为 GLOBAL→GLOBAL，不受影响；以后加跨文件声明时注意这一点）。
- 保存：defaults 与 global 互不牵制，各自写自己的版本；某个文件保存被拒绝时磁盘上仍是旧版本，下次启动重新迁移。删除原“defaults 未保存则 global 不升版”的处理（P2-12 的该部分）以及 `LeafGlobalConfig.loadedVersion`。reload 一次保存 override → defaults → global，全部尝试。
- 跨文件迁移与权限拒绝（已决定，按 Paper）：不加任何保护。Paper 的 `trySaveFileNode` 对每个文件独立保存，只对 `AccessDeniedException` 记 WARN 并沿用内存配置，各文件按自己的 `_version` 重试，也没有跨文件事务；其迁移都在单个文件内。Leaf 同样：以后若有跨文件声明，来源文件保存成功而目标文件被拒绝时，旧路径已从来源文件删除、值只在内存，下次启动不会恢复，属已接受的 Paper 风格边界（权限问题由管理员按保存 WARN 修复）。目前没有此类声明。

## P2-2 配置降级警告（已决定，已实施）

读取 `leaf-global.yml` 或已有 `leaf-world.yml` 时，若文件版本高于 `CURRENT_CONFIG_VERSION`，记录 WARN，列出文件路径、文件版本和当前支持版本。同时不执行迁移，并保留文件中的原版本，不改写为当前版本（P2-8）。见 04、08。

## 冲突处理决策

- “初始化失败停止服务器”适用于 Leaf raw 解析、Leaf path migration、binder、模块回调、最终保存（非 `AccessDeniedException`）。legacy purge 失败只警告（P2-10）。
- Gale migration 中，需导入的非 null 映射值若无法按旧 Gale 字段类型读取，按 Leaf 迁移错误使本次启动或世界加载失败；普通 YAML null 按缺键跳过。同源不得映射到不同目标，由定义测试保证（见 08）。其他 Gale 单阶段失败仍可恢复：该阶段不保留部分值，记录错误，继续其他阶段与正常 Leaf 启动；未产生待保存值的源文件沿用原归档策略；有值需持久化的先确认对应目标保存成功，再归档。
- Leaf 配置保存采用 Paper 风格：候选全部成功后才逐文件保存；`AccessDeniedException` 仅 warning 并使用内存配置；不创建 staged/rollback 文件。
- reload 与 Paper 一致在调用线程同步执行，不排队、不回滚。

## 已决定（已实施）：reload 与配置迁移

- **P0-2 reload 按文件版本重做未落盘的 Leaf 迁移。**
  - Paper 的设计：启动和 `reloadConfigs()` 走同一条路径——读取文件 → 按文件自身 `_version` 执行版本迁移 → `trySaveFileNode()`（权限拒绝只警告）。global、world defaults 和每个世界文件各有自己的 `_version`。保存失败时磁盘版本仍是旧值，reload 会自动重新迁移。
  - Paper 考虑到的部分（需处理）：版本迁移未落盘时，reload 不应丢失。reload 在内存中按 global、defaults 各自的版本迁移，仅遍历已加载世界，按每份候选 override 自身版本迁移。全部候选成功后在提交阶段保存；单个 override 权限拒绝时其磁盘进度不变，不阻止 defaults 与 global 保存各自的进度。
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
- **P2-12（已修订）。** 原“04 defaults 保存被拒时 global 版本不前进、08 跨文件迁移的权限拒绝处理”已被 P0-4 取代，不再保留：各文件独立记录版本，跨文件迁移遇权限拒绝按 Paper 处理（见 P0-4，不加保护）。06 `WORLD_DEFAULTS → GLOBAL` 后 override 旧键 warning 保留（不是保存保护，只是提示）。
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

**P2-31 `SentryDSN.onLoaded()` 把环境变量 `SENTRY_DSN` 写入配置字段 `sentryDsn`**

**Attribution: Leaf-introduced** — ver/26.2 的旧式 `SentryDSN` 同样在回调中改写 `sentryDsn`；注解式重写沿用，与 P1-16 的规则冲突（回调不得改写 `@ConfigInfo` 字段），但不在 P1-16 的模块清单内。

触发：设置了 `SENTRY_DSN` 环境变量后，首次快照（`loadAfterBootstrap()` 记录）保存的是环境变量值。因为 `dsn` 是字段级 `@HotReloadUnsupported`，reload 恢复的就是该值，运行效果一致；YAML 在绑定时已写入文件里的原值，不受影响。影响仅是规则一致性，没有观察到行为错误。可选方案：加 `@DoNotLoad` 的派生字段（例如 `effectiveDsn`），`SentryManager` 等读取点改读它；或接受为例外。按 11 的规则未修改。


## 实施记录

按 11 的阶段记录每轮完成情况、偏差与验证结果。

- **实施前基线（`config_paths.py` 对比 ver/26.2）：** ver/26.2 的选项在工作区全部存在；`scope changed` 仅 `misc.secure-seed.enabled`（见 P1-13）；`only in working tree` 为 Gale 映射引入的选项与 P2-30。`default changed` 的 4 项已核对等价：`async-pathfinding.reject-policy`（旧版将运行期算出的策略写入 YAML，新版写 `default` 并在运行期计算，已有显式值不受影响）、`async-unsafe-read-handling`（同为 `DISABLED`）、`disable-world-data-saving.worlds`（同为空列表）、`do-a-barrel-roll-installed-timeout`（旧版 YAML 默认 0，运行期 ≤0 改为 40；新版默认 40，运行效果相同）。
- **阶段 0（恢复可编译）已完成：** 起点提交：主仓库 `eca2da40`、paper-server `4a916ceb7`、leaf-server/src/minecraft/java `47ad474b`、paper-api `7773e3ae6`。完成 09/P2-11（`Services`、`MinecraftServer`、`Level`、`ServerLevel`、`InnerClassFieldDiscoverer`、`CraftServer`、`Server.Spigot` 的 Gale 引用与 `getGaleConfig()`）、P1-6（`LocatorBarRange`、`DensityFunctionCompiler` 转换；`network.global.*`、`gameplay.global.BookWriting` 引用更新，`BookWriting` 字段已改名为 `enabled`；删除 `run-async-tasks-sync`）、P1-10（`CraftBlock`、`ServerLevel` 改读 `asyncUnsafeReadHandlingPolicy`）、P1-13（`modules/misc/SecureSeed` 恢复为 global 并加 `@HotReloadUnsupported`，删除 world 版与 `LeafWorldConfig.secureSeed`，`Globals.setupGlobals()` 改读 global）、P2-30（删除 `Knockback.flushKnockback`）。`:leaf-server:compileJava` 与 `LeafTestSuite` 通过；`config_paths.py` 结果与基线一致（`scope changed` 为空，`only in ver/26.2` 为空，`default changed` 仍是已核对的 4 项）。`finalizeMigration()` 调用保留到阶段 5。09 的“未被 08 映射的 Gale 选项”功能对照尚未逐项列出，留待阶段 4 与 08 一并核对。
- **阶段 1（库行为确认与 binder）已完成：**
  - 库行为（`ConfigLibraryBehaviorTest`，rc.3 实测，已固化为测试）：① `ConfigFile.loadConfig()` 对缺失文件立即建文件（P1-9 成立，阶段 3 处理）；② `addDefault(path, def)` 把值重置为“文件读入值，否则默认值”：读入后 `set()` 写入的新键（迁移）被默认值覆盖，`set()` 改写的已有键被读入值覆盖（P1-8 成立）；显式 `null` 键在 `addDefault` 后仍为 `null`；③ `set(path, null)` 对文件读入的键无效：`contains()`、`get()` 仍返回读入值（只对仅 `set()` 过的键有效）。迁移“删除旧键”因此不会让旧键读不到，只是旧键未登记、保存时被丢弃；④ 保存只输出已 `addDefault` 或 `set()` 的键与注释，文件读入但未登记的键（含 `sec.other`）被丢弃，`set()` 后注释保留（P1-11 写回、P2-17 成立）；⑤ 带 fallback 的 getter 对 `abc`、`1.5`（int）、`oops`（boolean）静默返回 fallback，`getString` 把列表变成 `"[a, b]"`（P2-7、P2-9 成立）。
  - 实现：`ConfigBinder` 改为单一 `collect()`（P2-25）：global 启动、global reload、world defaults（启动/reload 候选）、world override 各一个薄入口，先收集 `PendingValue` 再 `applyAll()`，不再有 `ReloadValue`、`preparePendingValues()`、`GLOBAL_DEFAULT_VALUES`（P2-14、P2-15）。读取走 `util/ConfigValues.parse()`（P2-7、P2-9，`LeafConfigAccessor` 的带默认值 getter 共用），存在且非 null 的值不合法时抛出含文件、path、期望类型的 `IllegalArgumentException`。P1-8：`addDefault` 后对存在的值（迁移写入的、文件读入的）用 `set()` 写回，显式 `null` 写入默认值。P1-3：世界快照放到 `LeafWorldConfig.initialValues`（按需分配），global 仍是 binder 的静态快照。P2-22：`HotReloadUnsupported` Javadoc 已补充。P2-13：binder 中 null 默认值、重复 `@ConfigClassInfo`、模块类型相等、快照缺失检查均已删除；`loadAfterBootstrap()` 的 catch 不再记日志；`discoverWorldModules()` 早退已删。
  - 测试：`LeafTestSuite` 的 `@SelectPackages` 加入 `org.dreeam.leaf.config`；新增 `ConfigLibraryBehaviorTest`（7）、`ConfigBinderReloadTest`（16，binder 部分）、`ConfigModuleContractTest`（9）。`LeafConfig` 的 reload 流程仍是旧结构（阶段 5 改写），仅适配新 binder 入口；世界 reload 的快照来源已改为各 `LeafWorldConfig`。
  - 与计划的差异：`ConfigModuleContractTest` 为 null 默认值检查会初始化模块类（`Class.forName(..., true)`），当前所有模块的静态初始化可在无 bootstrap 的测试环境下运行；若以后有模块的静态初始化依赖 Minecraft，需改为只检查引用类型字段。
- **阶段 2（模块规则）已完成：**
  - P1-16：派生值改写入 `@DoNotLoad` 字段，运行期读取点已改：`FastRNG.active`（原 `enabled`，8 处读取）、`SparklyPaperParallelWorldTicking.effectiveThreads`（`DedicatedServer`）、`AsyncPathfinding.effectiveMaxThreads/effectiveQueueSize`（`AsyncPathProcessor`）、`MultithreadedTracker.effectiveThreads`（`AsyncTracker`）、`IceAndSnowChance.effectiveChance`（`RandomTickSystem`、`ServerLevel`）、`ConnectionMessage.effectiveJoinMessage/effectiveQuitMessage`（`PlayerList`）、`MCBugFix.effectiveMc301114MaxCombatEntries`（`CombatTracker`）、`RegionFormatConfig.effectiveCompressionLevel`（`EnumRegionFormat`）、`VanillaUsernameCheck.removeAllCheckActive`（`shouldSkipNonPlayerNameCheck()`、`ServerLoginPacketListenerImpl`）。旧搜索确认原字段已无模块外读取点。
  - 与计划列表的差异：① 并行世界 `logContainerCreationStacktraces`、`disableHardThrow` 的读取点（`AbstractContainerMenu`、`ServerPlayer`、`TickThread`）已自行检查 `enabled`，不需要回调里的 `enabled && x` 改写，因此只删除改写、不加派生字段；② `RegionFormatConfig.ioFlushDelay` 回调中从未写回（局部变量遮蔽了字段），无需改动；③ `ProtocolSupport.doABarrelRollInstalledTimeout` 只在回调内传给 `DoABarrelRollProtocol.init()`，改为局部表达式，没有派生字段。
  - P1-7：`FastRNG.worldgen` 只在首次 `onLoaded()` 决定一次（私有标记 `worldgenDecided`），reload 回调不再赋值；`enableForWorldgen` 仍是字段级 `@HotReloadUnsupported`。P2-23：`Level.leafConfig` 改为 `private final`，删除 `setLeafConfig()`。`ThrottleNaturalMobSpawning` 保持手工读取。
  - 测试：`ConfigModuleCallbackTest`（12）：FastRNG 两个方向的 worldgen 决定、派生值不写 YAML、各模块配置字段保持读入值、刷怪动态键与百分比单位。`config_paths.py` 对比 HEAD：只有 `flush-location-while-knockback-player` 删除与 `misc.secure-seed.enabled` WORLD → GLOBAL（均为阶段 0 的决策）。
  - 范围外发现（待决定）：见“待处理问题 P2-31”。
- **阶段 3（文件 IO、启动与备份）已完成：** P1-9：`ConfigFileIO.load()` 对 `Files.notExists(path, NOFOLLOW_LINKS)` 的路径返回 `new ConfigFile(file)`（实测不建文件、不建父目录，保存后与读入的文件一致，已加入 `ConfigLibraryBehaviorTest`），目录与失效软链接走读取路径并报错，不跟随软链接创建目标。P1-12：`createDirectory()` 去掉 catch 与 `dir.delete()`。P2-10：`purgeOutdated()` 逐个 `moveLegacy()`，`IOException` 只 WARN，仅有成功移动时才提示“已移入备份目录”；方法不再声明 `IOException`。P2-4：`ConfigBackupSession.move()` 改 `void`，`GaleConfigMigration.archive()` 同步调整。P2-13：`ConfigFileIO.save()` 删除同一文件保存两次的检查。P2-3 只确认，未改动。测试：`ConfigFileIOTest`（8）、`ConfigBackupSessionTest`（6，与归档无关部分）、`LeafConfigInitializationTest`（7，与版本无关部分：首次启动不建文件、非法标量停止启动且不建文件、defaults malformed 时不创建缺失的 global、`config` 是文件时不删除、legacy 无法移动只 WARN、legacy 移入备份、无 legacy 不建备份目录）。`LeafConfigInitializationTest` 使用测试任务的工作目录下的 `config/`，每次测试前后清理，并用反射清空 `LeafConfig` 的模块注册表（`loadConfig()` 每个进程只调用一次，重复调用会重复登记模块）。未在测试中覆盖 `loadAfterBootstrap()` 的保存顺序与权限拒绝，留到阶段 4/5（需要模块的 `onRegistriesLoaded()` 与保存失败的构造方式）。
- **阶段 4（版本与迁移）已完成：**
  - P0-1/P1-11/P2-2/P2-8/P2-15：新增 `util/ConfigVersion`；`LeafConfig.checkVersion(ConfigFile)` 按“版本号规则”处理已有文件（缺失 WARN、视为当前、保存时补写；无法解析抛 `IllegalArgumentException` 含路径与原文本；更高 WARN、不迁移、保留原值）。global 在 `loadConfig()` 中检查（文件不存在则视为当前版本，不警告），`LeafGlobalConfig` 改为接收 `VersionCheck`；每份已有 override 在 `loadWorldConfig()` 中按自身版本迁移，`needsSave = 迁移有变更 || 版本需要更新`，`initWorldConfig()` 保存。已删除 `previousConfigVersion`、`loadPreviousConfigVersion()`、单参数 `isConfigVersionBefore()`、`ConfigMigrationContext(null)` 回退、`LeafGlobalConfig` 的 `loadPreviousVersion`。**偏差：** `isConfigVersionAtLeast()` 单参数版本依赖已删除的 `previousConfigVersion`，改为与 `isConfigVersionBefore(stored, version)` 对应的两参数版本（无调用方）。override 绑定（`writeDefaults=false`）对存在的键 `set(path, raw)`，使重写 override 只含其显式键和版本（库只保存已登记或 `set` 的键）。
  - P1-8：`ConfigBinder`（阶段 1）加 `LeafGlobalConfig.structureConfig()` 修正：分类标题登记 `addDefault(category, null)` 会把分类重置为读入值，丢掉已迁移进分类的子树（Gale global 值首次启动即丢失；Leaf rename 在已有分类下同样丢失），现在登记后用 `set` 写回原值。端到端测试：Leaf rename、Gale global 值经 `loadConfig()`、保存、重读后保持。
  - P2-12：04 部分已实施：`saveGlobalFiles()` 先保存 defaults，再保存 global；defaults 为 `ACCESS_DENIED` 或失败时，global 以加载时的版本值落盘（内存仍是当前版本），两个文件都会尝试，失败合并为一个 `ConfigSaveException`（`ConfigSaveException` 构造方法改为 public，新增 `SaveReport.merge`）。06 部分：override 含 `WORLD_DEFAULTS → GLOBAL` 迁移走的旧键时 WARN。08 的跨文件迁移权限拒绝处理：目前没有跨文件声明，未实施。
  - P2-21：`CURRENT_CONFIG_VERSION` 保持 3.1，无代码改动。
  - P1-4/P2-13/P2-14（Leaf 迁移）：`LeafConfigMigration` 删除运行时的路径非空、同路径、父子重叠、同目标多来源、`migrate` 签名检查和快照恢复；保留旧键为 null/section、目标为 section 的数据检查；新旧键均存在且值不同时 WARN（保留新值、删除旧键），同值不告警。`collect()` 改为包级可见供定义测试使用。注意：库的 `set(old, null)` 对读入的键无效（阶段 1 实测），旧键仍可读但未登记，保存时被丢弃，链式 `A → B → C` 保存重读后只剩 `C`（已测）。
  - P0-3/P2-1/P2-18/P2-13/P2-14（Gale）：global/defaults 先收集再写入，`leafConfig.get(target) != null` 的目标不导入（异值 WARN，同值静默；null 视同缺键）；值按 Configurate 4.2.0 的标量规则转换（布尔接受 `t/yes/y/1/f/no/n/0` 和数字，整数接受整数值的浮点数，其余沿用 `ConfigValues`），非法映射值抛 `IllegalArgumentException`（含源文件、键、类型）：global/defaults 使启动失败，世界使本次加载失败并保留源，不写任何部分值。`resolveMappings()` 找不到目标字段直接抛出，不再跳过阶段。每个阶段对未映射且不是 `_version` 的键给一条 WARN。世界 override 没有可导入值（仅 `_version`、仅未映射、仅 null、空文件）不建文件；有值时写入值与当前版本，`ConfigFileIO.load()` 不再预建文件，保存失败不会留下遮蔽源文件的目标。**偏差：** 旧 Gale 字段类型取自 Leaf 目标字段类型（映射表里的类型相同），未另建旧类型表。
  - 测试：`LeafConfigMigrationTest`（9，版本比较、实际模块的声明一致性、四种作用域移动、override 只应用 WORLD_DEFAULTS→WORLD_DEFAULTS、链式、冲突 WARN、数据校验）、`LeafConfigMigrationBindingTest`（1）、`GaleConfigMigrationTest`（11）、`LeafWorldOverrideMigrationTest`（12，经 `LeafConfig.loadConfig()`/`initWorldConfig()`：无 override 不建文件、当前版本不重写、旧版本只升版本并只含显式键、缺版本 WARN 并补写一次、无法解析失败且不改文件、更高版本保留、各世界按自己的版本、非法值失败、Gale override 的建立/无值/非法值/已有 Leaf override、Gale global 与 Leaf rename 的端到端）、`LeafConfigInitializationTest` 增加保存顺序与 defaults 权限拒绝时 global 版本不前进（共 9）。新增测试辅助 `LogCapture`。
  - 仍未做（留给阶段 5）：reload 内存迁移与延迟保存（P0-2）、Gale 源文件的分阶段归档（P1-1）。reload 目前仍对 override 传 `migrateOverride=false`，不保存。
- **阶段 5（世界生命周期、reload 与归档）已完成：**
  - 同步 reload：`reloadAsync()` 改为 `reload(CommandSender)`（`ReloadCommand` 已改调），删除 future、`RELOAD_QUEUED_OR_RUNNING`、`RELOAD_TRANSACTION_LOCK`、`WorldReload`、`ReloadCommit`（P2-19，各世界不再换入 `ConfigFile`；defaults 保留 `setConfigFile()`）。核心逻辑拆为包级可测的 `reloadConfigs(List<LoadedLevel>)`，抛 `ReloadException(stage = CANDIDATE | CALLBACK | SAVE)`，`reload()` 只负责消息；这是为了在测试里不依赖 `MinecraftServer` 的接缝。
  - P0-2：候选阶段重新读取 global（先按其版本迁移 global/defaults，不重放 Gale）、defaults 和每个已加载世界的 override（按自身版本迁移，`loadWorldConfig(..., migrate=true)`），任一步失败则不改字段、不跑回调、不写盘。提交时需升级的 override → defaults → global 依次保存；override 权限拒绝只进报告，不阻止其余目标。
  - P1-2：提交后先 `applyAll()`，再运行回调，首个异常即停并抛 `CALLBACK`，不保存、不回滚；回调全部成功后才保存；`AccessDeniedException` 只报告，其他保存失败抛 `SAVE`（含完整报告），不报成功。
  - P1-1：删除 `GaleConfigMigration.finalizeMigration()` 及 `MinecraftServer` 中的调用；`loadAfterBootstrap()` 保存后调用 `archiveGlobalSources(report)`（对应 Leaf 文件不是 `SAVED` 的源保留），每次 `initWorldConfig()` 成功后调用 `archiveWorldSource(worldDirectory)`（权限拒绝或保存失败的世界源保留）；两个阶段共用 `loadConfig()` 的 backup session。`recordLeafPersistence()` 已删除。世界源只在世界成功加载之后归档：非法映射值使加载失败时保留源，已有 Leaf override、无可导入值、malformed 的源按原策略归档。
  - P1-5、P2-20 只确认，未改动。
  - 测试：`LeafConfigReloadTransactionTest`（10：同步完成、非法候选不改任何东西且不建缺失文件、删除键回到运行值并写回、不可热重载选项保持首次值但 YAML 保存新值、首个回调失败后后续回调不跑且不保存、权限拒绝、override 按自身版本迁移且未加载世界不动、当前版本 override 不重写且无 override 不建文件、override 权限拒绝不牵连 global、无法解析的 override 版本使 reload 失败）、`GaleArchiveTest`（6：global/defaults 保存后归档、Leaf 文件未保存则保留源、晚加载世界归档到同一 backup 目录、无可导入值归档且不建 override、保存失败保留源、非法值保留源）。未测试：最终保存发生非权限异常的 `SAVE` 阶段（候选的 `ConfigFile` 在 `reloadConfigs()` 内部创建，没有注入点；`ConfigFileIO` 的非权限失败路径已由 `ConfigFileIOTest` 覆盖）。
- **阶段 6（收尾）核对结果：**
  - `./gradlew :leaf-server:test --tests org.bukkit.support.suite.LeafTestSuite` 通过，测试报告中 10 列出的各测试类均已执行：`ConfigModuleContractTest`（9）、`ConfigBinderReloadTest`（16）、`LeafConfigInitializationTest`（9）、`ConfigBackupSessionTest`（6）、`LeafWorldOverrideMigrationTest`（14，位于 `org.dreeam.leaf.config` 包）、`LeafConfigReloadTransactionTest`（10）、`ConfigFileIOTest`（8）、`LeafConfigMigrationTest`（9）、`GaleConfigMigrationTest`（12）；另有 `ConfigLibraryBehaviorTest`（9）、`ConfigModuleCallbackTest`（12）、`GaleArchiveTest`（6）、`LeafConfigMigrationBindingTest`（1）。
  - `config_paths.py` 对比 ver/26.2：`only in ver/26.2` 0、`scope changed` 0、`type changed` 0；`only in working tree` 39 项为 Gale 映射引入的选项；`default changed` 4 项为 00 实施前基线已核对的等价项。`git diff --check` 在主仓库与三个嵌套仓库均无问题。
  - 依赖权限的测试（只读文件/目录）在当前环境执行；以 root 运行或不执行权限检查的环境会提前返回。
  - 待所有者完成：把 Minecraft 与 paper-server 的改动并入 Leaf config changes 补丁、为 paper-api 新建补丁（收录 `getGaleConfig()` 删除）、整体 review；完整启动服务器覆盖：全新安装、从 ver/26.2 配置升级、带 Gale 配置升级、`/leaf reload`、插件晚加载世界。
- **P0-4 已实施（defaults 自己的 `config-version`）：** `LeafConfig.fileVersion(file)`（global 与 defaults 共用，文件不存在视为当前版本）；`loadWorldDefaults`/`reloadWorldDefaults` 在绑定前把版本写入 defaults 文件（保存时只含已登记键和版本）；`LeafConfigMigration.migrate(global, defaults, modules, globalVersion, defaultsVersion)` 按来源文件版本判定；删除 `saveGlobalFiles()`（启动保存直接 `save(defaults, global)`，reload 一次 `save(overrides…, defaults, global)`）、`LeafGlobalConfig.loadedVersion`、`SaveReport.merge`。单元测试：`LeafConfigMigrationTest.everyFileIsMigratedByItsOwnVersion`、`LeafConfigInitializationTest` 的 defaults 版本用例（缺失/无法解析/更高/随 global 缺失、defaults 无法保存时各自保持版本）。e2e 新增 28–31；既有夹具里无版本的 defaults 补上版本。**注意：** 已有的无版本 defaults（旧 Leaf 分支生成的）升级后首次启动会有一次 WARN 并补写版本，属预期。

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
