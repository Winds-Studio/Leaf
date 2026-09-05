# 1. 将配置模块契约校验移出运行时 binder

## 修改文件

- `leaf-server/src/main/java/org/dreeam/leaf/config/ConfigBinder.java`
- 新增 `paper-server/src/test/java/org/dreeam/leaf/config/ConfigModuleContractTest.java`

## 为什么修改

配置模块由开发者维护，不应让生产服务器在初始加载、重载和每个世界加载时反复反射校验字段结构。此类问题应在测试阶段阻止合入。

## 具体修改

- 从 `ConfigBinder` 删除：
  - `validateField(...)`；
  - 所有调用点；
  - 仅为该方法保留的 `Modifier` 依赖。
- 保留运行时必需的反射访问：
  - `field.setAccessible(true)`；
  - 读取和写入字段；
  - 非法 enum、unsupported config type、配置文件解析错误等用户配置错误仍在运行时失败。
- 在测试模块实现 test-only `validateField` 等价规则：
  - 全局 `ConfigModule` 的 `@ConfigInfo` 字段必须是 mutable static；
  - `WorldConfigModule` 的 `@ConfigInfo` 字段必须是 mutable instance field；
  - `@DoNotLoad` 不得与 `@ConfigInfo` 共存；
  - `@ConfigInfo` 字段默认值不得为 null；
  - 每个 module 有 `@ConfigClassInfo`；
  - 每个 world module 在 `LeafWorldConfig` 中恰有一个 typed field；
  - 支持类型仅限 binder 当前支持的 primitive/包装类型、`String`、`List<String>`、enum。
- 测试使用已有 ClassGraph 依赖扫描 `org.dreeam.leaf.config.modules`，而不是复制生产类发现逻辑。
- 若 module 声明迁移方法，测试验证其签名为 `public static void migrate(ConfigMigrationContext)`；迁移方法只登记 path operation，不得直接加载、保存或修改配置文件。

## 与现有代码交互

- `LeafConfig.discoverGlobalModules()` 仍负责生产运行时发现。
- `ConfigBinder` 仍负责读取值、复制列表、处理 defaults 和反射赋值。
- 测试成为 module author 的结构性防线，不改变配置文件格式。

## 测试

- `ConfigModuleContractTest` 扫描所有实际模块。
- 为错误样例使用嵌套测试类验证 static/instance、final、null default、`DoNotLoad + ConfigInfo` 和 world-field 暴露规则均会失败。

## 可能的失败模式

- 新模块不运行测试时可在生产反射阶段失败；这是接受的开发流程风险。
- 测试扫描过宽可能加载非配置类；扫描结果必须只筛选 `ConfigModule` / `WorldConfigModule` 的 concrete 实现。
