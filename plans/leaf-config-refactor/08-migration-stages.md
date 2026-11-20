# 8. 将 Leaf 与 Gale migration 实现为按文件阶段的事务

## 修改文件

- `leaf-server/src/main/java/org/dreeam/leaf/config/migration/{LeafConfigMigration,ConfigMigrationContext}.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/migration/ConfigPathMigration.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/migration/gale/GaleConfigMigration.java`
- 新增 `paper-server/src/test/java/org/dreeam/leaf/config/migration/LeafConfigMigrationTest.java`
- 新增 `paper-server/src/test/java/org/dreeam/leaf/config/migration/gale/GaleConfigMigrationTest.java`

## 为什么修改

当前 Gale mapping 逐项直接写 target；若中途异常，已经写入的值会留在 Leaf candidate。Leaf migration 也需要明确 version gate、跨文件语义和 world override 规则。

## 具体修改

### 8.1 Leaf migration

- `CURRENT_CONFIG_VERSION` 更新为 `3.1`。
- `< 3.1` 时由拥有 option 的 module class 声明：
  - `OptimizeBlockEntities.migrate(...)`：`performance.optimise-block-entities` → `performance.optimize-block-entities`
  - `OptimizeRandomTick.migrate(...)`：`performance.optimise-random-tick` → `performance.optimize-random-tick`
- Leaf migration 在 raw global/defaults parse 后、defaults materialization 前执行。`LeafConfigMigration` 不集中维护 option path；它创建带 stored config version 的 `ConfigMigrationContext`，反射调用 module class 的静态 `migrate(ConfigMigrationContext)`，收集全部 operation。
- `ConfigMigrationContext` 必须支持：global → global、global → world defaults、world defaults → global、world defaults → world defaults；最后一种同时生成 world override 的同路径 lookup/materialize operation。
- 先验证全部 migration operation：
  - source/target type；
  - 非空路径；
  - 同文件路径不可相同或父子重叠；
  - source 是 option，不是 `ConfigSection`。
- 对 global/defaults 的 Leaf migration 作为一个内存 transaction：
  - 所有 module 先登记 operation；
  - context 统一计算全部 source value、target conflict 和删除操作，并构造供 binder 使用的 migration lookup；
  - binder 加载 target `new-path` 时优先新 path；新 path 缺失时 lookup 提供 old path/source config 的值；
  - 全部 binder 与 callback 成功后才 materialize：将采用的旧值写入 target，删除旧 path；
  - 任意收集、验证、绑定或 callback 错误都不 materialize、也不写入任何 Leaf YAML。
- 冲突时保留现代新路径值，删除旧路径。

### 8.2 Gale global/defaults migration

- 分离两个独立 stage：
  - `gale-global.yml → leaf-global.yml`
  - `gale-world-defaults.yml → leaf-world-defaults.yml`
- 每个 stage：
  1. 完整加载和解析源 Gale 文件；
  2. 解析该 stage 的全部 mapping targets；
  3. 收集所有迁移值；
  4. 记录每个 target 的存在性和原始值；
  5. 仅在前四步成功后应用全部 mapping；
  6. 应用异常时逆序恢复 target snapshot。
- global stage 失败：global candidate 不保留任何该 stage 值，仍继续 defaults stage。
- defaults stage 失败：defaults candidate 不保留任何该 stage 值，不回滚已成功 global stage。
- 成功 Gale stage 覆盖 Leaf 自动 defaults，保留管理员 Gale 自定义值。

### 8.3 Gale world override migration

- 每个已加载世界是独立 stage。
- 已有 `leaf-world.yml`：跳过内容迁移，Leaf 文件优先。
- malformed Gale 文件：记录 error，不生成 Leaf override。
- 不再预先 `Files.createFile(leafPath)`：
  - 在内存构造 Leaf override candidate；
  - 完整 mapping、bind 和验证成功后才直接首次写入；
  - 非 `AccessDeniedException` 写入失败则无 Leaf override 文件，世界使用 Leaf defaults；`AccessDeniedException` 则继续使用迁移后的内存 override，并保留 Gale 源文件。
- 仅 `_version` 的 Gale 文件不生成 Leaf override。
- 每个 world stage 失败不影响其他世界。

## 与现有代码交互

- `LeafConfig.loadConfig()` 负责 Leaf module 静态 migration 方法的收集、migration lookup 与 Gale global/defaults raw candidate 顺序。
- `LeafConfig.initWorldConfig()` 负责已有 Leaf override rename 或缺失 Leaf override 时的 Gale migration。
- `GaleConfigMigration.finalizeMigration()` 继续只处理 loaded levels，并使用第 5 步共享 backup session。

## 测试

- Leaf 3.0 到 3.1、同文件冲突、四种 global/defaults operation、world override 同路径 operation、section source error。
- module 静态 migration 方法的收集顺序不影响结果；新 path 优先、缺失新 path 时使用 old path 值、成功后 materialize 新 path 并删除旧 path。
- Gale global/defaults mapping 在中途失败时，无部分值残留。
- valid/malformed/existing Leaf/only-version Gale world override 的全部组合。
- archive 仍在 global/defaults 或非权限错误的 world stage 失败后执行；唯一例外是 world override 因 `AccessDeniedException` 未能写入时，必须保留其 Gale 源文件。未加载世界不处理。

## 可能的失败模式

- 逐条直接 `set()` 会留下部分 Gale 迁移结果。
- 过早创建 Leaf world override 会留下空文件。
- 对 world defaults rename 未同步 override 会使显式配置失效。
