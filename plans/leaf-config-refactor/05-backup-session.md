# 5. 使用共享 backup session 统一 purge 与 Gale 归档目录

## 修改文件

- `leaf-server/src/main/java/org/dreeam/leaf/config/migration/ConfigBackupSession.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/LeafConfig.java`
- `leaf-server/src/main/java/org/dreeam/leaf/config/migration/gale/GaleConfigMigration.java`
- 新增 `paper-server/src/test/java/org/dreeam/leaf/config/migration/ConfigBackupSessionTest.java`

## 为什么修改

旧实现中 `purgeOutdated()` 和 `GaleConfigMigration.archive()` 各自生成时间戳，同一次迁移可能分散到多个 backup 目录。

## 实现状态（当前源码）

- `ConfigBackupSession.create(configDirectory)` 在 `loadConfig()` 中只调用一次，路径为 `config/backup-<yyMMdd-HHmmss>`。
- 目录在第一次 `directory()` / `move()` 时才确定：若同名目录已存在，依次尝试 `-1`、`-2`……；没有需要移动的文件时不创建目录。
- `move(source, relativeTarget)`：
  - 拒绝绝对路径和以 `..` 开头的相对路径；
  - 创建父目录后 `Files.move`；失败时抛 `IOException`，源文件保留；
  - 当前总是返回 `true`（调用方已先检查源文件存在）。
- `purgeOutdated(session)`：把工作目录下的 `pufferfish.yml`、`leaf.yml`、`leaf_config/` 移入 session；当前 IO 失败致命，按 04 P2-10 改为警告并保留原文件。
- `GaleConfigMigration.migrate(...)` 保存 session；`finalizeMigration(server)` 使用同一 session：
  - 归档 `gale-global.yml`、`gale-world-defaults.yml`；
  - 归档启动期已加载世界的 `gale-world.yml` 到 `world-overrides/<world-context>/gale-world.yml`；`world-context` 为世界目录相对工作目录的路径，工作目录之外的世界使用去掉根的绝对路径；
  - 对应 Leaf 目标权限拒绝的源文件不归档（error 日志）；
  - 归档 IO 失败只记录 error，源文件保留，不终止启动。

## P1-1 分阶段归档（已决定，待实施）

- **P1-1** `finalizeMigration()` 只在启动期执行一次（`POSTWORLD` 插件启用前）。之后创建的世界完成 Gale world 迁移后，源文件不会被归档，此后每次启动都会 warning“Leaf override 已存在”。待拆除 `finalizeMigration()` 及其全局遍历：在 `loadAfterBootstrap()` 末保存 defaults、global 后处理对应的 Gale 源；每次 `initWorldConfig()` 处理完该世界的配置后，在新世界目录处理其 `gale-world.yml`。插件晚加载同样归档；未加载世界不处理。两个阶段复用 `loadConfig()` 创建的 session，备份路径不变。
  - 有迁移值须落盘时，以对应 Leaf 目标 `SAVED` 为归档前提；权限拒绝或其他保存失败均保留该源。无需写入目标的情况（如已有 Leaf override、Gale 仅有 `_version`、无映射命中、malformed 跳过）沿用原归档策略，不以写出 Leaf 文件为前提。需导入的映射值无法按旧 Gale 字段类型读取时，本次加载失败并保留源，不按“无需写入”归档（见 08）。其他 Gale 单阶段错误仍可恢复；归档 IO 失败仍只记录 error 并保留源。
- 归档失败后 Gale 源文件残留，下一次启动会再次执行 Gale global/defaults 迁移；必须配合 P0-3（Gale 值不覆盖已有 Leaf 值），否则管理员在 Leaf 中做的修改会被覆盖。

## P2-4 `move()` 改为 `void`（已决定，待实施）

`move()` 只有两种结果：移动成功返回 `true`，或抛 `IOException`（非法备份路径、目标已存在、创建目录或移动失败），没有返回 `false` 的分支，因此改为 `void`。失败仍以异常交给调用方，处理策略不变：Gale 归档记录 error 并保留源文件；legacy purge 失败记 WARN 并保留原文件（P2-10）。调用方同步调整：`purgeOutdated()` 在每次移动后直接设 `legacyFound = true`；`GaleConfigMigration.archive()` 移动后直接记录 WARN。

## 待处理问题与边界

- Windows 下工作目录外的世界，`world-context` 会丢失盘符；不同盘符下同名路径的世界会冲突，`Files.move` 抛 `FileAlreadyExistsException`，源文件保留，可接受。

## 测试

- 同时存在 legacy Leaf/Pufferfish 文件与多份 Gale 文件：global/defaults 保存后归档，晚加载世界仅在其加载时归档，所有移动目标共享同一 backup root。
- 没有需要备份的文件：不创建 backup 目录。
- 同名 backup 目录已存在：使用 `-1` 后缀。
- 目标路径非法、移动失败：源文件保留，错误可见。

## 可能的失败模式

- 为每次移动重新创建时间戳会分裂备份。
- eager 创建目录会在没有迁移时留下空 backup。
- purge 移动一部分后失败：已移动的文件在 backup root，未移动的留在原处并 WARN，启动继续；下次启动只会再次尝试移动剩余文件。
