# 5. 使用共享 backup session 统一 purge 与 Gale 归档目录

## 修改文件

- 新增 `leaf-server/src/main/java/org/dreeam/leaf/config/migration/ConfigBackupSession.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/LeafConfig.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/migration/gale/GaleConfigMigration.java`
- 新增 `paper-server/src/test/java/org/dreeam/leaf/config/migration/ConfigBackupSessionTest.java`

## 为什么修改

当前 `purgeOutdated()` 和 `GaleConfigMigration.archive()` 各自生成时间戳；Gale 甚至可为同一次迁移的不同文件生成多个 backup 目录。

## 具体修改

- 新增 `ConfigBackupSession`：
  - 在 `LeafConfig.loadConfig()` 仅创建一次；
  - 路径格式为 `config/backup<timestamp>`；
  - 若同名目录已存在，追加稳定数字后缀；
  - 目录延迟创建：没有需要移动的文件时不生成空 backup 目录。
- `ConfigBackupSession.move(source, relativeTarget)`：
  - 验证 target 为相对路径且不允许 `..`；
  - 创建 parent；
  - 移动文件；
  - 返回是否确实发现并处理源文件；
  - 失败保留源文件并抛出/记录上下文。
- `purgeOutdated(session)`：
  - 接收 session，不自行生成 backup path；
  - 将 `pufferfish.yml`、`leaf.yml`、`leaf_config/` 放入该 session；
  - IO 失败向上抛出，触发致命初始化失败。
- `GaleConfigMigration.migrate(...)` 接收同一 session 并保存到 migration 生命周期；
- `GaleConfigMigration.finalizeMigration(...)` 使用同一 session：
  - archive `gale-global.yml`、`gale-world-defaults.yml`；
  - archive 已加载世界的 `gale-world.yml` 到 `world-overrides/<world-context>/gale-world.yml`。
- 同一次启动中，purge 与 Gale migration 的所有备份只能出现在一个 backup 根目录中。

## 与现有代码交互

- `LeafConfig.purgeOutdated()` 不再自己格式化日期。
- `GaleConfigMigration.archive()` 不再每次 `new Date()`。
- `finalizeMigration()` 仍在所有 loaded levels 建立后运行。

## 测试

- 同时存在 legacy Leaf/Pufferfish 文件与多份 Gale 文件：断言所有移动目标共享同一 backup root。
- 没有需要备份的文件：不创建 backup folder。
- 目标路径非法、移动失败：源文件保留，错误可见。

## 可能的失败模式

- 为每次移动重新创建时间戳会分裂备份。
- eager 创建 session 目录会在没有迁移时留下空 backup。
- purge 成功一部分后后续失败会留下已移动 legacy 文件；这是允许的备份副作用，但服务器必须停止且所有文件仍在同一 backup root。
