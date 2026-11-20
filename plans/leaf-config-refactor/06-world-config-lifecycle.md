# 6. 保持 world defaults 仅作为模板，并扩展 Leaf world override rename migration

## 修改文件

- `leaf-server/src/main/java/org/dreeam/leaf/config/LeafConfig.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/LeafWorldConfig.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/migration/LeafConfigMigration.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/util/ConfigFileIO.java`
- 新增 `paper-server/src/test/java/org/dreeam/leaf/config/migration/LeafWorldOverrideMigrationTest.java`

## 为什么修改

world defaults 不能直接作为任意 level 的 active config。并且当 world-defaults 中的 option 路径重命名时，已有 `leaf-world.yml` 的显式 override 也必须使用新路径，否则 override 会失效或变为未使用的旧键。

## 具体修改

- 保留现有独立对象模型：
  - `worldDefaultsConfig` 只作为模板；
  - 每个世界都创建新的 `LeafWorldConfig`；
  - 先复制 defaults module fields，再覆盖该世界 `leaf-world.yml` 中显式存在的值；
  - defaults 中的 `List` 等可变值必须复制。
- 即使世界没有 `leaf-world.yml`：
  - world config 对象仍然独立；
  - accessor 可以指向 defaults 文件，但 active module fields 不能引用 defaults module fields；
  - 不生成 world override 文件。
- 从 `LeafWorldConfig` 删除已删除的 `WorldConfigExample` import 和字段；只保留实际 world modules。
- `LeafConfigMigration` 扩展 world override migration：
  - migration registry 继续支持 global/defaults 的四种方向；
  - 对于 `WORLD_DEFAULTS -> WORLD_DEFAULTS` 的 rename，额外生成同路径 `WORLD_OVERRIDE -> WORLD_OVERRIDE` rename；
  - 不自动将 `WORLD_DEFAULTS -> GLOBAL` 或 `GLOBAL -> WORLD_DEFAULTS` 扩展到 override；此类跨作用域语义必须未来显式声明。
- `LeafConfigMigration.migrateWorldOverride(ConfigFile overrideConfig)` 协调对应 world module 的 migration hook：
  - 使用 global stored config version 判断 `< 3.1`；
  - 仅在 world 初始加载时对已经存在的 `leaf-world.yml` 调用；
  - 在 binder merge 前执行；
  - 返回是否发生变更。
- 实际 rename path 由对应 world module class 的静态 `migrate(ConfigMigrationContext)` 声明；`LeafConfigMigration`/`ConfigMigrationContext` 仅提供 version gate、作用域选择、冲突处理、lookup 与最终 materialize。`WORLD_DEFAULTS -> WORLD_DEFAULTS` operation 自动在 world override context 以 `WORLD_OVERRIDE -> WORLD_OVERRIDE` 执行；override 的新 path 缺失时，binder 从同一 override 文件的旧 path 合并显式值，而不是把 defaults 当成 override。
- `LeafConfig.loadWorldConfig()`：
  - 对存在的 Leaf override：raw load → world override migration → bind/merge；
  - migration 发生变更时，仅在 bind/merge 全部成功后原子写回该 override；
  - migration 或 bind 失败时抛出，作为 Leaf world 初始化致命错误；由于保存尚未开始，原 override 文件不改写。
- world override migration 的保存遵循 Paper 风格：在 migration 与 bind 成功后直接保存；`AccessDeniedException` 只记录 warning 并继续使用迁移后的内存 world config。该 world 的 `gale-world.yml` 不能在 Leaf override 成功持久化前归档，以免权限问题造成下次启动丢失迁移源。
- 不在 `/leaf reload` 中执行 path migration；migration 是启动升级流程，避免 reload 意外改写 override 文件。

## 与现有代码交互

- `ConfigBinder.applyWorldDefaults()` 已负责字段复制。
- `LeafConfig.initWorldConfig()` 仍先处理 Gale world migration；已有 Leaf override 则执行 Leaf override rename，不执行 Gale 内容迁移。
- `Level.leafConfig()` 保持世界独立 active config。

## 测试

- 两个世界没有 override：模块实例、列表和值不共享。
- defaults path rename + 已有 `leaf-world.yml`：defaults 和 override 都迁移到新路径。
- override 新旧键冲突：保留新键、删除旧键。
- world migration bind 失败：原 override 文件不改写。非 `AccessDeniedException` 保存失败：作为保存错误报告，不承诺跨文件或文件内容 rollback；`AccessDeniedException`：world 内存配置可用，源 Gale 文件保留。
- 未创建 override 的世界：不生成文件。

## 可能的失败模式

- 直接复用 defaults module 会产生世界间串值。
- 只迁移 defaults 而不迁移 override，会让用户 override 静默失效。
- 对跨作用域迁移盲目复制到 override 会改变每世界语义。
