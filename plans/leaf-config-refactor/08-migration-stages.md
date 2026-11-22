# 8. 将 Leaf 与 Gale migration 实现为按文件阶段的事务

## 修改文件

- `leaf-server/src/main/java/org/dreeam/leaf/config/migration/{LeafConfigMigration,ConfigMigrationContext}.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/migration/gale/GaleConfigMigration.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/LeafConfig.java`（世界加载时读取 override 版本、迁移与保存）
- `leaf-server/src/minecraft/java/net/minecraft/server/MinecraftServer.java`（删除 `finalizeMigration()` 调用）
- 新增 `paper-server/src/test/java/org/dreeam/leaf/config/migration/LeafConfigMigrationTest.java`
- 新增 `paper-server/src/test/java/org/dreeam/leaf/config/migration/gale/GaleConfigMigrationTest.java`

（原计划中的 `ConfigPathMigration.java` 不存在，不需要。）

## 为什么修改

Gale mapping 逐项直接写入目标，中途异常会留下部分值；Leaf migration 需要明确版本门槛、跨文件语义和 world override 规则。

## 实现状态

### 8.1 版本号（当前实现；override 门槛待按 P0-1 修改）

- `CURRENT_CONFIG_VERSION = "3.1"`，global 与 world 共用，存于 `leaf-global.yml` 的 `config-version`。只在新登记迁移时递增（P2-21）；新增选项由默认值填充，不升版。
- `ConfigVersion`：按 `.` 分段的非负整数，逐段数值比较，缺段补 0（`3.10 > 3.9`，`3 == 3.0`）。
- 当前实现：缺失或无法解析视为 `0`（无法解析时 warning），会执行所有迁移。按 00“版本号规则”修改为：缺失视为当前版本并 WARN、不迁移；无法解析则拒绝加载；更高则不迁移、不改写版本。
- 判断入口：
  - global/defaults（P0-4）：global 与 defaults 各用自己文件的 `config-version` 建一个 `ConfigMigrationContext`；一条迁移由其来源文件的版本决定，先执行来源在 global 的操作，再执行来源在 defaults 的；
  - world override：`ConfigMigrationContext(null)` 回退到 `LeafConfig.previousConfigVersion`（启动时由 `LeafGlobalConfig` 记录的旧版本）。

### 8.2 Leaf path migration（已实现操作收集与应用；绑定、写回缺口见 P1-8）

- 模块声明：`public static void migrate(ConfigMigrationContext)`，内部 `isBefore("3.1")` 时调用 `migrate(Scope source, oldPath, Scope target, newPath)`。现有声明：
  - `OptimizeBlockEntities`：`performance.optimise-block-entities` → `performance.optimize-block-entities`（GLOBAL → GLOBAL）
  - `OptimizeRandomTick`：`performance.optimise-random-tick` → `performance.optimize-random-tick`（GLOBAL → GLOBAL）
- 收集：`LeafConfigMigration.collect()` 按模块发现顺序（global 模块按类名排序，随后是 world 模块）反射调用；方法必须为 static 且返回 `void`，否则致命错误。
- 校验（应用前）：
  - 路径非空；
  - 同作用域时新旧路径不同且互不为父子；
  - 旧 path 存在时其值不能为 null 或 `ConfigSection`；
  - 新 path 存在时不能为 `ConfigSection`；
  - 同一目标不能被不同来源登记（完全相同的重复登记允许）。
- 应用：直接修改 raw 内存 `ConfigFile`（不是原计划的 lookup + 延迟 materialize）：
  - 旧 path 存在、新 path 不存在 → 新 path 写入旧值；
  - 无论新 path 是否存在，都删除旧 path（冲突时保留新值）；
  - 当前实现：应用前对所有涉及的 path 做快照，应用中出现 `RuntimeException` 时恢复全部快照值。P2-14 决定删除：失败时调用方整体丢弃该配置（启动失败、世界加载失败、reload 候选丢弃），恢复结果不可见。
- 落盘：global/defaults 在 04 的最终保存阶段写入；逐世界 override 的保存时机见 06，reload 见 07。
- 支持 global → global、global → defaults、defaults → global、defaults → defaults；override 只应用 defaults → defaults（见 06）。

### 8.3 Gale global/defaults migration（已实现分阶段映射；绑定、写回缺口见 P1-8）

- 在 Leaf path migration 之后、`LeafGlobalConfig` 构造之前运行，写入 raw Leaf 文件。
- 两个独立阶段：`gale-global.yml → leaf-global.yml`、`gale-world-defaults.yml → leaf-world-defaults.yml`。
- 每个阶段：
  1. 通过 `ConfigPaths.fieldPath()` 把映射解析为 Leaf 当前路径（新 path），失败则跳过该阶段；
  2. 加载源文件，malformed 时 error 日志并跳过；
  3. 收集全部值，记录目标原值，再统一 `set()`（当前为无条件覆盖，待按 P0-3 修改）；
  4. 失败时恢复目标原值。P2-14 决定删除第 3 步的原值记录与第 4 步：按下方 Gale 迁移规则先收集、校验全部映射值，校验失败时尚未写入；写入阶段只剩内存 `set()`。
- 一个阶段失败不影响另一个阶段。
- 权限拒绝的 Leaf 目标对应的 Gale 源文件不归档（`recordLeafPersistence`）。

### 8.4 Gale world override migration（当前实现）

- 每次加载世界（启动期和运行期）时，在 `initWorldConfig()` 中执行：
  - 没有 `gale-world.yml` → 跳过；
  - 已有 `leaf-world.yml` → warning 并跳过（Leaf 优先）；
  - malformed 源文件 → error，使用 defaults；
  - 只有 `_version` → 跳过，不生成文件；
  - 否则在内存中构造 override → 应用映射 → 绑定 → `ConfigFileIO.save()`。当前加载缺失目标时就会创建空文件，并非只在最终保存时创建（见 04 P1-9）。
- 保存权限拒绝：使用内存配置，并保留 Gale 源文件。
- 其他异常（包括非权限的保存失败）：记录 error，返回 null，调用方转入普通 Leaf 加载；若目标已成为空文件，会表现为使用 defaults。残留目标会让下次先命中 `leafFile.isFile()`，即使 Gale 源还在也不再转换。当前 finalize 还可能归档未标记保留的源。
- 各世界相互独立。

## P0-1 逐世界迁移的版本来源（已决定，阶段 4 已实施）

保留 `initWorldConfig()` 入口和模块的 `isBefore(...)` 写法，将 raw `leaf-world.yml` 自己的 `config-version` 传给 `migrateWorldOverride()` 的收集阶段，不再回退到 global 的旧版本。同时删除仅为该回退存在的 `previousConfigVersion`、`loadPreviousConfigVersion()`、单参数 `isConfigVersionBefore(String)`、`ConfigMigrationContext(null)` 分支与 `LeafGlobalConfig` 的 `loadPreviousVersion` 参数（P2-15）。迁移与版本一起写回；权限拒绝只影响本文件。缺失、无法解析或更高的版本按 00“版本号规则”处理，与 global 相同。详见 06。

## P2-2 配置降级警告（已决定，已实施）

比较解析后的版本：文件版本高于 `CURRENT_CONFIG_VERSION` 时，在更新版本或保存前记录 WARN，包含文件路径、文件版本和当前支持版本。global/defaults 检查 global 的版本；每个已有 override 在实际加载或 reload 时检查自身版本，不扫描未加载世界。版本更高时不执行迁移，且保存时保留原版本，不改写为当前版本（P2-8）。

## Gale 迁移规则（已决定，阶段 4 已实施）

- **P0-3：global/defaults 的 Gale 映射只补缺失的 Leaf 值。** 当前 `applyMappings()` 不跳过已有目标，会改写本轮的输出值；后续默认值登记可能重新取回磁盘原值，不能断言每次最终保存都被 Gale 覆盖（P1-8）。修改：
  - 收集阶段，若 `leafConfig.contains(targetPath)`，跳过该映射，不写入 Gale 值；
  - 两者值不同时 warning，列出 Gale 源文件、Leaf path、保留的 Leaf 值、被忽略的 Gale 值；值相同时不警告；
  - 检查 raw Leaf 文件，不把本次绑定时注入的默认值当作已有值。以前已写进文件的值也保留，不推断是谁写的；已有 `leaf-world.yml` 时整份优先，不从 Gale 按键补缺；
  - Leaf path migration 先于 Gale 执行，由旧 Leaf path 迁移来的值同样视为 Leaf 值。
  - 结果：Gale 迁移可重复执行，不会覆盖管理员在 Leaf 中的修改。
- **映射定义与 Leaf rename 使用同一条同源规则。** 在测试中检查 `registerMappings()` 的原始登记列表，global、world 分开：同一 Gale 源路径不得对应不同 Leaf 目标，完全相同的登记不算多目标。目标按解析后的 Leaf 路径比较；不能只检查 `resolveMappings()` 返回的 Map，`put(oldPath, leafPath)` 已遮蔽较早的登记。不新增运行时查重；Gale 源和 Leaf 目标是不同文件，不涉及链式执行。
- **源值按旧 Gale 字段的读取语义判断。** 依据 Leaf 26.3 `GaleConfigurations`、Paper `Configurations` 和 Configurate 4.2.0（`YamlConfigurationLoader.loadInternal`、`MapConfigValue.set`、`ObjectMapperImpl.load0`）：
  - 普通映射键的 YAML null（`key:` / `key: ~`）在创建节点时按缺键处理。导入时跳过，不写 null 或 Gale 默认值，不算命中，不报冲突；世界文件仅含这些键时不建 Leaf override。与 Leaf rename 对显式 null 的规则不同。
  - 对按 P0-3 需要导入的非 null 值，按旧字段类型读取并迁移转换后的值，不按 YAML 原始类型一刀切。例如布尔字段的字符串 `"no"` 是合法的 `false`；`"maybe"` 不是合法布尔，`"abc"` 不是合法整数。不能直接复制原值再让 Leaf binder 静默回默认。
  - 旧 Gale 反序列化会拒绝的映射值，按 Leaf 迁移错误处理：写入前使本次加载失败，并报告源文件、源键及期望类型。global/defaults 或启动期世界失败则启动失败；晚加载世界仅使本次世界加载失败。不提交部分映射，不归档该源，不留下遮蔽重试的空 override。此例外仅限实际参与导入的非法映射值；整份 YAML 无法解析仍按既定 Gale 阶段策略处理，已有 Leaf override 仍整份优先。
- **P2-18：未映射的键给 WARN。** 每个阶段（global、defaults、各世界 override）读取 Gale 源文件后，对存在但不在映射表中、且不是 `_version` 的键记一条 WARN，列出源文件与键路径，说明这些选项已不再支持、不会迁移。只读不改，不影响迁移与归档。
- **P2-1：没有实际映射值就不建世界 override。** 旧目录中的 `gale-world.yml` 由现有目录迁移搬运。`initWorldConfig()` 仅在没有 `leaf-world.yml` 且至少命中一个映射时生成带当前版本的文件；不扫描 universe，不在旧目录生成或搬运 `leaf-world.yml`。
- **P1-1：按世界归档。** 启动世界和插件晚加载的世界都在各自加载时使用同一 backup session；有值需落盘时，先确认目标保存成功再归档。无须写目标的情况见 05。

## P1-4 Leaf 路径迁移：定义测试与冲突日志（已决定，已实施）

仅适用于 `LeafConfigMigration`，不改变 Gale → Leaf 映射。按 P2-13，8.2 中针对规则定义的运行时校验（路径非空、同作用域同路径、单条规则父子重叠、不同来源同目标）一并移入定义测试并从运行时删除；仅保留数据校验：旧键为 null 或配置节、目标为配置节。Gale 映射目标字段不存在同样由 Gale 定义测试保证，`migrateStage()` 不再捕获并跳过该阶段。

- **定义测试检查实际模块**，用能触发相应迁移的旧版本收集规则。以 `(scope, path)` 标识端点：同一来源不能指向两个不同目标（完全相同的重复登记不算）；不同操作的源、目标端点在同一作用域内不能互为父子。按路径段判断：`foo` 与 `foo.bar` 重叠，`foo` 与 `foobar` 不重叠；不同作用域的同名路径不冲突。
- **链式迁移由作者保证执行顺序**，不因前一目标等于后一来源而拒绝，也不在运行时排序。用真实 `ConfigFile`、与生产一致的收集顺序验证 `A → B`、`B → C`：初始只有非默认值 `A`，保存重读后仅 `C` 保留原值，`A`、`B` 均不存在。以此确认支持，不以 mock 或仅检查登记顺序代替；若未通过，不依赖该链。
- **旧键显式为 null 是非法输入**（如 `key: ~`）。保留现有迁移失败行为；启动时失败是预期，不把 null 当缺失，也不清理后继续。
- **唯一新增的运行时行为是冲突 WARN**：新旧键均存在时仍保留新值、删除旧键。仅在两值不同时，于删除前记录源和目标的作用域、路径，以及丢弃的旧值和保留的新值；同值不告警。在该分支加注释说明原因：新键可能是管理员已作出的选择，因此优先保留，仅在丢弃不同旧值时提醒。

## P1-8 迁移写入的非默认值可在绑定时丢失（已决定，阶段 1、4 已实施）

**归因：Leaf 引入** — Leaf 用 ConfigurationMaster `v2.0.0-rc.3` 的同一 `ConfigFile` 做 raw 迁移和默认值登记；Paper 在 Configurate node 上做 transformation，不经过这条 `set → addDefault` 链。

`set()` 写输出树；`CMConfigSection.addDefault()` 用读入树的 `existingValues.getOrDefault(key, default)` 重写输出值。例：旧 `performance.optimise-block-entities: false`、新键不存在，迁移先写 `false`，binder 登记默认值后又成为 `true`。Gale global/defaults 写入缺失目标也受影响。已有磁盘 Leaf 新键可能被 `addDefault` 取回，不能以此认定 P0-3 已实现；新建的 Gale world override 则走 `writeDefault=false`，不是同一覆盖路径。

已决定：保持“先迁移、后绑定”的顺序，不改为先绑定再 apply 旧值；在绑定层修正默认值登记。结果由实现保证，不新增运行时校验（如绑定后对比字段与迁移值）；下方测试仍保留。

修复目标：默认值只填真正缺失的键，分类标题登记也不能覆盖本轮新增的子树。绑定和序列化必须看到同一份迁移结果，包括已删除的旧键；`set(old, null)` 仅移除输出项，不保证原读入键从内存查询消失。无需恢复旧 lookup 设计。重写已有 override（含仅提升版本）须保留其他显式键，不混入 defaults：库的 `convertToMap()` 枚举的是输出键，并非所有可读取的键。当前两条 rename 都是 global，后一点是实施 P0-1 时的验收边界。

## 待处理问题

### 其他迁移边界

- **跨文件迁移与权限拒绝（已决定，按 Paper，不加保护）：** global ↔ defaults 迁移时，若目标文件权限拒绝而来源文件保存成功，旧 path 已从来源文件删除，值只存在于内存，下次启动不会恢复。Paper 对每个文件独立 `trySaveFileNode`（权限拒绝只警告、沿用内存配置），也没有跨文件保护，其迁移限于单个文件；Leaf 接受同样的边界。各文件独立记录版本（P0-4），不做“来源文件暂不升版”“目标保存成功前不删除旧 path”之类的补偿。现有声明只有 GLOBAL → GLOBAL，暂不触发；新增跨文件声明时在发行说明或注释中提示这一点即可。

## 测试

- 降级警告：当前支持 `3.9` 时，读取版本 `3.10` 的 global 或 override 均记录带路径和两个版本号的 WARN；相等或较旧的有效版本不触发该警告。版本更高时不执行迁移，保存后文件版本仍为 `3.10`（P2-8）。

- 版本比较：`3.9 < 3.10`、`3 == 3.0`；global 与已有 override 缺版本：WARN、不迁移、保存时补写当前版本；新建文件不警告；版本非法：拒绝加载。
- global/defaults 3.0 → 3.1：旧 path 迁移；新旧冲突时保留新值、删除旧值；四种作用域组合。override 用有版本的测试文件模拟后续升级，覆盖同路径迁移，不假设存在 3.0 的历史世界文件。
- P1-8：用异于默认值的旧 Leaf 值及 Gale global/defaults 值，断言绑定字段、保存后 YAML、再次读取三者一致；另测磁盘已有新键、新的 Gale world override、仅升版时保留无关显式键。
- P1-9：Gale 有实际映射，首次绑定或首次写入失败时不发布目标占位文件；保留源、修复原因后重新加载能再次迁移。仅未知键不建目标；已有 Leaf 文件不因重试逻辑被删。
- 数据校验：旧键为 section 或显式 null、目标为 section 均失败。定义测试（P1-4、P2-13）：实际定义的路径非空、同作用域新旧路径不同、不同来源同目标、同源多目标与父子重叠（含单条规则内），Gale 映射目标字段存在；正序链式迁移的落盘结果；冲突同值无 WARN、异值有上述 WARN，两者均保留新值并删除旧键。
- Gale global/defaults：单阶段失败无残留；目标缺失才写入，已有同值不警告、异值保留并警告；Gale 源残留再次运行也不覆盖管理员修改后的 Leaf 值。
- Gale 定义：检查真实登记列表；用同源不同目标的反例确认不会被解析后的 Map 掩盖。源值：global/defaults 的 null 不导入，逐世界只有 null 时不建目标；合法 `"no"` 导入为 `false`。混合合法值与不可解析的已映射值时，本次启动或晚加载世界失败，错误可定位，源仍在且目标无部分写入。
- Gale 未映射键：含未映射键的 global、defaults、world 源文件各记一条列出路径的 WARN；仅含 `_version` 或全部已映射时无 WARN（P2-18）。
- Gale world：正常、malformed、已有 Leaf override、仅 `_version`、仅未映射 key（修改后）、保存权限拒绝时保留源文件。
- 旧插件世界启动时未加载：不转换也不归档；之后首次加载才从搬到新目录的 Gale 文件生成带版本的 override，成功后归档。

## 可能的失败模式

- 未先完成全部收集与校验就开始写入，会在校验失败时留下部分 Gale 迁移结果。
- 过早创建 Leaf world override 会留下空文件。
- 对 world defaults rename 未同步 override 会使显式配置失效。
- 版本门槛与实际迁移进度不一致会导致迁移被永久跳过。
