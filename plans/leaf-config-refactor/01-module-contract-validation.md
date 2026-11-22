# 1. 将配置模块契约校验移出运行时 binder

## 修改文件

- `leaf-server/src/main/java/org/dreeam/leaf/config/ConfigBinder.java`
- 新增 `paper-server/src/test/java/org/dreeam/leaf/config/ConfigModuleContractTest.java`

## 为什么修改

配置模块由开发者维护，不应让生产服务器在初始加载、重载和每个世界加载时反复反射校验字段结构。此类问题应在测试阶段阻止合入。

## 实现状态

- 已完成：`ConfigBinder.validateField(...)` 及其调用点已删除。
- 仍保留在运行时的检查：
  - `registerGlobalDefaults()`：global 字段默认值为 null 时抛错（源码 `TODO: move it to test`）；
  - `bindWorld()`：world 字段默认值为 null 时抛错；
  - `bind()` / `collectGlobalReload()` / `ConfigPaths`：缺失 `@ConfigClassInfo` / `@ConfigInfo` 时抛错；
  - `readValue()`：不支持的字段类型、非法 enum 字符串。
- **P2-13（已决定，待实施）：开发者定义错误移至测试，删除运行时检查。** 删除两处 null 默认值检查，以及 `bind()`、`collectGlobalReload()` 对 `@ConfigClassInfo` 的重复检查（取注释前先计算路径，缺失时由 `ConfigPaths` 自然抛错）。其余移至测试的项见 00 P2-13。`ConfigPaths` 的缺注解异常、`readValue()` 末尾的不支持类型分支是代码流程本身的结尾，不是额外校验，保留；非法 enum 是用户数据校验，保留。
- 未完成：`ConfigModuleContractTest`。

## 具体修改（剩余）

- 在测试中实现 test-only 契约规则：
  - global `ConfigModule` 的 `@ConfigInfo` 字段必须是 mutable static；
  - `WorldConfigModule` 的 `@ConfigInfo` 字段必须是 mutable instance field；
  - `@DoNotLoad` 不得与 `@ConfigInfo` 共存；
  - `@ConfigInfo` 字段默认值不得为 null；
  - 每个 module 有 `@ConfigClassInfo`；
  - 每个 world module 在 `LeafWorldConfig` 中恰有一个 typed field；
  - 支持类型仅限 binder 当前支持的 `boolean/int/long/double` 及包装类型、`String`、`List<String>`、enum；
  - 同一配置文件内，所有模块解析出的 option path 不重复，且互不为父子路径（`ConfigPaths.fieldPath` 结果）。
- 若 module 声明 `migrate` 方法，签名必须为 `public static void migrate(ConfigMigrationContext)`。签名由测试保证；按 P2-13 删除 `LeafConfigMigration.collect()` 中对 static 与 `void` 的运行时检查。“只登记 operation”是约定，由代码审查保证。
- 测试使用 ClassGraph 扫描 `org.dreeam.leaf.config.modules`，只筛选 `ConfigModule` / `WorldConfigModule` 的 concrete 实现；扫描时不初始化类（ClassGraph 元数据 + `Class.forName(name, false, loader)`），避免模块静态初始化触碰 `MinecraftServer` / Bootstrap。

## 与现有代码交互

- `LeafConfig.discoverGlobalModules()` 仍负责生产运行时发现（自带的 `getClasses()` 包扫描，按 simple name 排序）。
- `ConfigBinder` 仍负责读取值、复制列表、处理 defaults 和反射赋值。

## 测试

- `ConfigModuleContractTest` 扫描所有实际模块。
- 为错误样例使用嵌套测试类，验证 static/instance、final、null default、`DoNotLoad + ConfigInfo`、world-field 暴露、路径重复/重叠规则均会失败。

## 可能的失败模式

- 新模块不运行测试时可在生产反射阶段失败；这是接受的开发流程风险。
- 测试扫描过宽或初始化类会加载依赖 Minecraft bootstrap 的代码。
