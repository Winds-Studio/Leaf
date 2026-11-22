# 9. 删除 Gale 配置 runtime 与公开 API

## 修改文件

已删除：

- `leaf-server/src/main/java/org/galemc/gale/configuration/` 下全部配置类（`GaleConfigurations`、`GaleGlobalConfiguration`、`GaleWorldConfiguration`、`GaleRemovedConfigurations`）

仍需修改：

- `leaf-server/src/minecraft/java/net/minecraft/server/Services.java`（record component、两个构造/工厂调用、accessor）
- `leaf-server/src/minecraft/java/net/minecraft/server/MinecraftServer.java`（`galeConfigurations` 字段与赋值）
- `leaf-server/src/minecraft/java/net/minecraft/world/level/Level.java`（`galeConfig` 字段、accessor、构造参数与赋值）
- `leaf-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java`（`super(...)` 中的 `GaleWorldConfiguration` lambda）
- `paper-server/src/main/java/io/papermc/paper/configuration/mapping/InnerClassFieldDiscoverer.java`（`galeWorldConfig(...)`）
- `paper-server/src/main/java/org/bukkit/craftbukkit/CraftServer.java`（`getGaleConfig()` override）
- `paper-api/src/main/java/org/bukkit/Server.java`（`Server.Spigot#getGaleConfig()`）

## 实现状态

- 配置类已删除，但上述引用仍在，当前源码树无法编译。本子计划是恢复可编译状态的前置项，应优先完成。
- 当前 `MinecraftServer` 仍调用 `GaleConfigMigration.finalizeMigration(this)`；待 05 的分阶段归档就位后移除此调用，不移除 Gale 文件迁移。

## 为什么修改

Gale runtime 仍通过服务初始化、world 构造、Paper mapper 和 Bukkit API 暴露。保留文件迁移不代表 runtime 类型应继续存在。

## 具体修改

- 移除 `Services` 的 Gale record component、构造、factory 与 accessor。
- 移除 `MinecraftServer.galeConfigurations`。
- 移除 `Level.galeConfig`、creator 参数和构造调用。
- 从 `ServerLevel` 的 `super(...)` 调用中移除 Gale lambda。
- 删除 `InnerClassFieldDiscoverer.galeWorldConfig(...)`。
- 删除 `Server.Spigot#getGaleConfig()` 与 CraftServer override。
- 最终不允许残留以下编译期引用：`GaleConfigurations`、`GaleGlobalConfiguration`、`GaleWorldConfiguration`、`galeConfigurations`、`galeConfig()`、`getGaleConfig()`。

## 已决定与实施核对

- **P2-11 直接删除 `Server.Spigot#getGaleConfig()`（已决定）：** 不保证 Gale 配置 API 兼容性，不保留过渡实现。已编译的插件调用时会抛 `NoSuchMethodError`，属预期。
- **功能对照清单（实施时核对）：** 需要确认原先读取 Gale 配置字段的每个功能，都已改为读取 08 映射到的 Leaf module 字段，且默认值一致。当前 `galeConfig()` 已无读取调用点；未被 08 映射的 Gale 选项需列出，并说明是删除还是改为固定行为。

## 与现有代码交互

- Leaf world config 继续由 `Level.leafConfig()` 提供。
- Gale migration 只使用 `gale-*.yml` 文件名与 Leaf module 映射，不依赖已删除的 runtime 类型。

## 测试

- 不加静态源码扫描测试（P2-16）：Gale 配置类已删除，残留引用会直接编译失败。以编译验证构造函数、record 和 Bukkit override 匹配。

## 可能的失败模式

- 只删除类而保留 record component 或 override 会导致编译失败（当前状态）。
- 误删非配置相关的 Gale 来源功能会超出任务范围。
