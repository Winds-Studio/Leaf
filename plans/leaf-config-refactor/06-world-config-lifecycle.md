# 6. 保持 world defaults 仅作为模板，并扩展 Leaf world override rename migration

## 修改文件

- `leaf-server/src/main/java/org/dreeam/leaf/config/LeafConfig.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/LeafWorldConfig.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/migration/LeafConfigMigration.java`
- 新增 `paper-server/src/test/java/org/dreeam/leaf/config/migration/LeafWorldOverrideMigrationTest.java`

## 为什么修改

world defaults 不能直接作为任意 level 的 active config；world-defaults 中的 option 重命名时，已有 `leaf-world.yml` 的显式 override 也必须迁移到新路径，否则会静默失效。

## 实现状态

### 已完成：独立对象模型

- `worldDefaultsConfig` 只作为模板（`Source.WORLD_DEFAULTS`）。
- 每个世界创建新的 `LeafWorldConfig`（`Source.WORLD_OVERRIDE`）：先 `applyWorldDefaults` 复制 defaults 字段（列表复制），再绑定 `leaf-world.yml` 中显式存在的值。
- 没有 `leaf-world.yml` 时：config 对象仍独立，`configFile` 指向 defaults 文件，不生成 override 文件。
- `LeafWorldConfig` 已不含 `WorldConfigExample`。

### 已完成：override rename migration

- `LeafConfigMigration.migrateWorldOverride(overrideConfig, worldModuleClasses)`：
  - 调用各 world module 的静态 `migrate()`，只保留 `WORLD_DEFAULTS → WORLD_DEFAULTS` operation，并把它们应用到 override 文件自身；
  - 不把 `WORLD_DEFAULTS → GLOBAL` 或 `GLOBAL → WORLD_DEFAULTS` 扩展到 override；
  - 版本判断：`collect(null, ...)` 使用内存中的 `previousConfigVersion`（启动时从 global 读取的旧版本）。
- `LeafConfig.loadWorldConfig(dir, defaults, migrateOverride)`：raw load override → （`migrateOverride` 为 true 时）迁移 → 绑定 → 有变更时 `ConfigFileIO.save(override)`。
- `initWorldConfig()` 使用 `migrateOverride=true`；reload 使用 `false`。
- 有 `gale-world.yml` 而没有 `leaf-world.yml` 时走 Gale 迁移（见 08）；已有 Leaf override 时只做 Leaf rename。

## P0-1 在世界加载时按文件版本迁移（已决定，阶段 4 已实施）

- global/defaults 和 override 共用代码里的 `CURRENT_CONFIG_VERSION`，但 `leaf-global.yml` 只记录 global/defaults 的进度。每份 `leaf-world.yml` 记录自己的 `config-version`；不能用 `previousConfigVersion` 判断某个世界是否已迁移。
  - 与 Paper 一样分两步：先由现有世界目录迁移把旧 `gale-world.yml` 搬到目标维度目录，再在 `ServerLevel` 构造时由 `initWorldConfig()` 处理配置内容。启动维度和插件之后通过 `createWorld` 加载的世界走同一路径；不遍历未加载的世界，也不移动 global/defaults 的保存时机。
  - 有 `leaf-world.yml`：raw load → 读取该文件版本 → 在绑定前按版本执行 `WORLD_DEFAULTS → WORLD_DEFAULTS` 操作 → 绑定成功后将 override 和当前版本写回同一文件。旧版本即使没有命中 rename，也要更新版本；缺版本时也写回一次补上版本（P1-11）；已有当前版本则不为迁移重写文件。保存权限拒绝时内存继续使用迁移值，下次加载按磁盘版本重试，不影响 global 或其他世界。
  - 无 `leaf-world.yml`：只有实际产生显式 override 时才建文件，首次保存就带当前 `config-version`；Gale 转换也遵守此规则（见 08）。版本是元数据，不算 override。已有文件的版本按 00“版本号规则”：缺失则 WARN、视为当前版本、不迁移，并写回版本；无法解析则本次世界加载失败，报告文件路径；更高则 WARN、不迁移、不改写。不以 global 版本兜底。

## P0-2 reload 的迁移范围（已决定，已实施）

- 当前 reload 不重新执行迁移。 override 迁移结果因 `AccessDeniedException` 未落盘时，reload 从磁盘读取旧 path，世界静默退回 defaults。
  - 按 Paper 设计处理：Paper 的 `reloadConfigs()` 与启动走同一路径（读取 → 版本迁移 → `trySaveFileNode`），未落盘的迁移会在 reload 时重新执行。
  - 修改：reload 只遍历已加载世界。global/defaults 用候选 global 的版本，各 override 用各自文件的版本；共用世界加载时的内存迁移逻辑，但必须延迟到提交阶段才保存（见 07）。
  - reload 不重放 Gale 转换。若权限拒绝导致新 override 只存在于内存，reload 后该世界使用 defaults，下次加载世界时从保留的 Gale 源重试。

## P1-5 世界 override 加载失败与 Paper 一致（已决定，保持现状）

Paper `PaperConfigurations.createWorldConfig()` 只把 `IOException`（含 Configurate 的解析、映射异常）包成 `RuntimeException`，从 `ServerLevel` 构造中抛出；`trySaveFileNode()` 仅对 `AccessDeniedException` 警告并继续使用内存配置，其他保存错误重抛。启动期因此为致命错误；运行期 `createWorld` 把异常交给调用方，构造失败的世界不会被注册。Paper 未为此增加清理、回退或重试。

Leaf `initWorldConfig()` 在同一构造位置调用，已有 Leaf override 的解析、迁移、绑定或非权限保存失败都包成 `RuntimeException` 抛出，权限拒绝仅警告，已与 Paper 一致。保持现状：不增加容错回退、额外的 `ServerLevel` 注册检查或清理逻辑。Gale 转换仍按 08：需导入的非法映射值使本次加载失败，其他 Gale 阶段错误可恢复。

## 待处理问题

- **P2-12**（见 00）`WORLD_DEFAULTS → GLOBAL` 迁移后，override 中的旧 key 会静默失效。不自动扩展到 override，但检测到 override 含有已迁移走的旧 key 时应 warning。

## 测试

- 两个世界没有 override：模块实例、列表和值不共享。
- defaults path rename + 已有 `leaf-world.yml`：defaults 和 override 都迁移到新路径。
- override 新旧键冲突：保留新键、删除旧键；两值不同时 warning，相同时不告警（P1-4）。
- 跨重启晚加载：global 已升级，插件世界 B 未加载，其文件不变；重启后首次 `createWorld(B)` 仍按 B 自己的旧版本迁移。对照世界 A 已是当前版本，不重写。
- 旧版本未命中 rename：只更新版本后从磁盘重读，全部原有显式值仍在、未注入 defaults；不要只断言内存 getter（08 P1-8）。已有 override 缺版本：WARN、不迁移，并写回一次，之后文件带当前版本且原有显式值不变，再次加载不再警告；版本无法解析：该世界加载失败，文件不改写；版本更高：WARN、不迁移、版本不被改小。
- 迁移结果未落盘后 reload：世界仍使用迁移后的值（P0-2 修改后）。
- world migration 绑定失败：原 override 文件不改写。
- 普通世界无显式 override：不生成文件。Gale 转换首次成功时同时写显式值和版本，不写继承的 defaults；绑定/首次保存失败时不能留下遮蔽源文件的空目标（04 P1-9）。

## 可能的失败模式

- 直接复用 defaults module 会产生世界间串值。
- 只迁移 defaults 而不迁移 override，会让用户 override 静默失效。
- 对跨作用域迁移盲目复制到 override 会改变每世界语义。
