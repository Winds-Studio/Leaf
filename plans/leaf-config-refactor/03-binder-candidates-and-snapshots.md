# 3. 重构 binder：首次值快照、重载删除保留与候选值模型

## 修改文件

- `leaf-server/src/main/java/org/dreeam/leaf/config/ConfigBinder.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/LeafWorldConfig.java`（持有实例快照）、`LeafConfig.java`（在初始化与 reload 时传入对应实例）
- 新增 `paper-server/src/test/java/org/dreeam/leaf/config/ConfigBinderReloadTest.java`

## 为什么修改

reload 应先构建纯候选数据，全部验证成功后再写入运行字段；缺失的 global/defaults 键不应回退到代码默认值。

## 实现状态（当前设计）

### 代码默认值

- `GLOBAL_DEFAULT_VALUES`：模块发现时（`registerGlobalDefaults`）记录 global 字段代码默认值，仅用于启动期绑定。P2-15 决定删除：启动期绑定直接以 `field.get(null)` 为默认值，与 world 一致。

### 首次成功初始化快照

- `INITIAL_GLOBAL_RELOAD_VALUES`：以 `Field` 为键；在 `loadAfterBootstrap()` 中 `onRegistriesLoaded()` 成功后记录。
- `INITIAL_WORLD_RELOAD_VALUES`：以 module 实例 identity 为键（`IdentityHashMap`）；world defaults 在 `loadConfig()` 绑定后记录，每个世界在 `initWorldConfig()` 绑定成功后记录（含 Gale 迁移生成的配置）。
- 仅记录字段级或类级 `@HotReloadUnsupported` 的字段；列表复制保存；`putIfAbsent` 保证只记录第一次。
- 初始化失败时不会到达记录点。

### 候选值模型

- 不新增 `ReloadCandidateValue` 类。当前候选值为 `ConfigBinder.ReloadValue(target, field, value)`，提交时 `preparePendingValues()` 一对一转为字段相同的 `PendingValue` 再 `apply()`；构造两者与 `apply()` 时各复制一次列表。
- P2-14（已决定，待实施）：只保留 `PendingValue`（候选阶段收集、提交阶段 `apply()` 的待应用值），删除 `ReloadValue` 与 `preparePendingValues()`。构造时复制一次列表，`apply()` 直接写入该副本，避免 defaults、世界实例和快照共享可变列表。因为 reload 不回滚（见 07），不记录旧值；“在锁内捕获旧值以避免 ABA”的原设计已不需要。

### 缺失键策略

- global reload（`collectGlobalReload`）：缺失键以当前 static 字段值为 fallback，`readValue(..., writeDefault=true)` 写回该值及注释。
- world-defaults reload（`bindWorldDefaultsForReload`）：缺失键以 active defaults module 当前值为 fallback，并写回 defaults 文件。
- world override：缺失键继承 candidate defaults；override 文件不补全默认项（`writeDefault=false`）。
- 启动期：global 缺失键使用代码默认值；world defaults 缺失键使用新 module 实例的字段初值。
- 注意（设计取舍，需在文档中说明）：删除某个 global/defaults 键再 reload，会写回 reload 前的运行值，而不是代码默认值；“删除键恢复默认值”只在不经过 reload 直接重启时成立。

### 不可热重载字段

- 候选值改为首次快照值（`initialReloadValue`），候选 YAML 中的新值仍随候选文件保存，供下次重启使用。
- 只有 global、world defaults 与全部已加载世界的候选都成功解析和绑定后，才会保存（见 07）。
- 快照缺失时抛 `IllegalStateException`，此时 reload 在提交前失败。

### path migration 与 binder 的关系（修正原计划）

- 原计划中的“migration lookup：binder 读取新 path 缺失时由 context 提供旧 path 值，并在全部成功后再 materialize”未采用。
- 当前是在绑定前对 `ConfigFile` 调用 `set(new, value)` / `set(old, null)`，binder 直接读新 path；没有缺新键时读取旧键的 lookup。选择内存迁移而不采用 lookup 本身不是遗漏，但现有库的读入值和待保存值并非同一张表：`addDefault()` 可能抹掉刚迁移的值；仅延迟显式 `save()` 也不能保证读候选时不建文件。分别见 08 P1-8、04 P1-9。

## P2-25 合并绑定路径（已决定，待实施）

`bind()`（含 `global` 布尔分派）、`bindWorld()`、`bindWorldDefaultsForReload()`、`collectGlobalReload()` 都是“遍历字段 → 计算路径 → 取注释 → `readValue(fallback)`”，只差 fallback 来源与直接写字段还是收集。合并为一个“读取模块 → `PendingValue` 列表”的函数，参数为 fallback 来源（global：`field.get(null)`；defaults：新实例或活动实例；override：候选 defaults）与是否写默认值和注释；启动期也先收集再 `apply()`。不可热重载字段的快照替换、P2-7/P2-9 的值检查都放在这一个函数里。

## P1-3 快照的归属与生命周期（已决定，待实施）

- **global**：保留服务器级 `INITIAL_GLOBAL_RELOAD_VALUES`，不放进 `LeafGlobalConfig`。reload 会替换 global 配置实例，但不会重新初始化服务器；新实例仍使用启动时捕获的有效值。
- **world defaults 和各世界**：移除静态 `INITIAL_WORLD_RELOAD_VALUES`，分别由各自的 `LeafWorldConfig` 持有首次值。绑定成功后，遇到第一个需要快照的配置字段才分配存储（字段或类有 `@HotReloadUnsupported`，且字段未被 `@DoNotLoad` 排除）；无标注就不分配。快照仅在内存中，列表仍复制。
- **普通 reload**：binder 从当前活动的 defaults 或世界实例读取快照。候选实例只提供可重载字段的新值，不记录、替换或重建活动实例的快照；候选失败时也不影响它。
- **卸载再加载**：新世界实例先继承当前有效的 defaults，再绑定磁盘上的显式 override，绑定结果就是该实例的首次值，不复制旧世界或 defaults 的快照。配置层不从静态集合持有世界实例；旧实例不再被引用时，其快照也可回收，不需要卸载回调或弱键 map。

## P2-7 已存在但不可解析的标量（已决定：拒绝，待实施）

**归因：Leaf 沿用依赖行为** — `ConfigBinder.readValue()` 使用 ConfigurationMaster rc.3 带 fallback 的 getter；01 的模块声明类型检查并不校验用户 YAML 的标量值。

例如 `int: abc` 或布尔项填 `"oops"`，getter 返回传入的 fallback，不抛候选错误。global/defaults 在 reload 中通常沿用活动值，world override 可回退到继承值；reload 仍可能报告成功，磁盘原文本未必被纠正。不要与非法 enum（会报错）或缺键（有明确回退规则）混为一谈。已核实（rc.3 字节码）：`getInteger` / `getLong` / `getDouble(path, def)` 捕获 `NumberFormatException` 后静默返回 `def`；`getBoolean(path, def)` 只认不区分大小写的 `true` / `false`，其他文本静默返回 `def`。`LeafConfigAccessor` 中会 WARN 的单参数 getter 不在绑定路径上。

已决定：拒绝**存在但不可解析**的 `boolean` / `int` / `long` / `double` 值。

- 判定沿用现有解析：取键的字符串形式，布尔仅接受 `true` / `false`（不区分大小写），数字用 `Integer` / `Long` / `Double.parseXxx`。现可解析的引号数字字符串（如 `'10'`）继续接受；整数字段填 `1.5` 等解析失败的值拒绝。
- 缺键和显式 null 仍按缺键处理，沿用上方回退规则；`String` 不受影响；enum 保持现有报错。
- 失败作为绑定错误抛出，报文件、配置 path、期望类型和原文本：启动期致命；reload 在候选阶段失败，不提交、不保存；world override 按 P1-5 使本次世界加载失败。
- 一处实现：`LeafConfigAccessor` 的带默认值 getter 与 `ConfigBinder.readValue()` 的 `writeDefault=false` 分支共用同一个解析帮助方法，不再调用库的带 fallback getter。手工读取的 `ThrottleNaturalMobSpawning` 键经 accessor，同样被拒绝。
- Gale 导入已按 08 以旧 Gale 字段类型判定，不受此条影响。
- **P2-9（已决定）同时拒绝结构不符的值。** 库的 `getString()` 对任意值做 `String.valueOf`，会把列表或配置节变成 `"[a, b]"` 之类文本。解析帮助方法先取原始值判断结构：标量字段（含 `String`、enum）遇到 `List` 或 `ConfigSection` 拒绝；`List` 字段原始值不是 `List` 则拒绝。缺键和显式 null 仍按缺键回退。
- 报错复用同一个异常构造：把 `readValue()` enum 分支中的 `throw new IllegalArgumentException("Invalid value ...")` 提取为帮助方法，补上文件路径，由 enum、P2-7 标量解析失败与 P2-9 结构不符共用，统一包含文件、配置 path、期望类型和原文本。

## 测试

- 删除 global option 后重载：字段和写回 YAML 都使用重载前值。另测 P2-7：`int: abc`、整数字段 `1.5`、布尔 `"oops"` 在启动、reload 和 world override 中均失败，报错含文件、path、类型；reload 失败后运行值与磁盘文件不变；引号数字 `'10'` 与大写 `TRUE` 仍被接受；显式 null 按缺键回退。P2-9：`String` 字段写成列表或配置节、`List` 字段写成标量、`boolean` 字段写成列表，均失败且报错格式与非法 enum 一致。
- 删除 world-default option 后重载：defaults 和未覆盖的世界值保留重载前 defaults 值。
- 删除 world override option 后重载：该世界改为继承 defaults。
- 字段级和类级 `@HotReloadUnsupported` 均保持首次成功初始化值；YAML 保存新值。
- 回调或 world bind 失败后不产生首次快照。
- 两个世界同一不可热重载字段首次绑定为 `2` 和 `4`，两份 override 都改为 `8` 后连续 reload，运行值仍为 `2` 和 `4`；只卸载并重新加载第二个世界后，运行值为 `2` 和 `8`。未覆盖的世界从当前有效 defaults 取首次值。
- 无标注的世界模块不分配快照；global 实例被连续替换后，标注字段仍恢复服务器启动时的值。

## 可能的失败模式

- 继续使用代码默认值会覆盖管理员已运行的配置。
- 未复制列表会使 defaults、世界实例和快照共享可变值。
- 若 binder 仍从静态集合持有世界 module，仅把快照字段移进配置对象并不能消除引用。
