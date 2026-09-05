# 9. 删除 Gale 配置 runtime 与公开 API

## 修改文件

删除：

- `leaf-server/src/main/java/org/galemc/gale/configuration/GaleConfigurations.java`
- `leaf-server/src/main/java/org/galemc/gale/configuration/GaleGlobalConfiguration.java`
- `leaf-server/src/main/java/org/galemc/gale/configuration/GaleWorldConfiguration.java`
- `leaf-server/src/main/java/org/galemc/gale/configuration/GaleRemovedConfigurations.java`

修改：

- `leaf-server/src/minecraft/java/net/minecraft/server/Services.java`
- `leaf-server/src/minecraft/java/net/minecraft/server/MinecraftServer.java`
- `leaf-server/src/minecraft/java/net/minecraft/world/level/Level.java`
- `leaf-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java`
- `paper-server/src/main/java/io/papermc/paper/configuration/mapping/InnerClassFieldDiscoverer.java`
- `paper-server/src/main/java/org/bukkit/craftbukkit/CraftServer.java`
- `paper-api/src/main/java/org/bukkit/Server.java`

## 为什么修改

Gale runtime 仍通过服务初始化、world constructor、Paper mapper 和 Bukkit API 暴露。文件迁移保留不代表 runtime 类型应继续存在。

## 具体修改

- 移除 `Services` 的 Gale record component、构造、factory 与 accessor。
- 移除 `MinecraftServer.galeConfigurations`；保留纯文件 migration 的 `finalizeMigration(this)`。
- 移除 `Level.galeConfig`、creator 参数和构造调用。
- 从 `ServerLevel` 的 `super(...)` 调用移除 Gale lambda。
- 删除 `InnerClassFieldDiscoverer.galeWorldConfig(...)`。
- 删除 `Server.Spigot#getGaleConfig()` 与 CraftServer override。
- 最终不允许以下编译期引用残留：
  - `GaleConfigurations`
  - `GaleGlobalConfiguration`
  - `GaleWorldConfiguration`
  - `galeConfigurations`
  - `galeConfig()`
  - `getGaleConfig()`

## 与现有代码交互

- Leaf world config 继续由 `Level.leafConfig()` 提供。
- Gale migration 仅使用 `gale-*.yml` 文件名与 Leaf module mapping，不依赖删除的 runtime 类型。

## 测试

- 添加静态 source scan test 或在 `ConfigModuleContractTest` 的辅助测试中确认不存在上述 runtime 引用。
- 编译期验证构造函数、record 和 Bukkit override 匹配。

## 可能的失败模式

- 只删除类而保留 record/component/override 会造成编译失败。
- 误删非配置 Gale 来源功能会超出任务范围。
