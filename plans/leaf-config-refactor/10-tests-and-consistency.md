# 10. 测试执行与最终一致性审查

## 修改文件

- `paper-server/src/test/java/org/dreeam/leaf/config/...` 下新增的测试文件。
- 不需要修改 `leaf-server/build.gradle.kts`：JUnit、Mockito、ClassGraph、`@TempDir` 支持已存在。

## 为什么修改

用户已明确允许生成测试和验证；此前“仅人工验证”的计划决策被此授权替代，但 patch 生成与无关任务仍不允许执行。

## 具体修改

新增测试覆盖：

- module contract validation；
- 初始加载失败不生成/不部分覆盖 global/defaults；
- global/defaults 删除键后的 reload fallback；
- first-load hot reload snapshot；
- 不可热重载项的 YAML 新值仅供下一次启动使用，且必须在所有 Leaf 候选文件成功加载后才保存；
- reload 事务、重复 reload 拒绝、候选完成后的直接保存与 AccessDenied 内存降级；
- 最终保存结果报告：错误/warning 分别列出已保存、权限拒绝及失败的 Leaf 文件；
- world template 独立性；
- Leaf world override rename migration；
- module 声明的四种 Leaf path migration、旧 path merge、新 path 优先与延迟 materialize；
- Gale 每文件 stage rollback；
- 单一 backup root；
- Gale runtime/API 无残留引用。

## 验证命令

实现完成后允许执行受限测试：

```text
./gradlew :leaf-server:test --tests org.dreeam.leaf.config.ConfigModuleContractTest
./gradlew :leaf-server:test --tests org.dreeam.leaf.config.ConfigBinderReloadTest
./gradlew :leaf-server:test --tests org.dreeam.leaf.config.LeafConfigInitializationTest
./gradlew :leaf-server:test --tests org.dreeam.leaf.config.LeafConfigReloadTransactionTest
./gradlew :leaf-server:test --tests org.dreeam.leaf.config.migration.LeafConfigMigrationTest
./gradlew :leaf-server:test --tests org.dreeam.leaf.config.migration.LeafWorldOverrideMigrationTest
./gradlew :leaf-server:test --tests org.dreeam.leaf.config.migration.gale.GaleConfigMigrationTest
```

最后进行：

```text
git diff --check
git status --short
```

## 一致性结论

- 仍保留：注解式配置、3.1 migration、Gale 值优先于自动 Leaf defaults、Leaf world override 优先、Gale 文件归档、删除 Gale API/runtime、世界独立 defaults merge。
- 已修正：runtime field validation、删除键 fallback、async reload 安全、迁移时序表述、ABA 风险、world override rename、测试授权、统一 backup folder。
- 未扩展：不修改 patch 文件、不生成 patch、不改变无关 Gale 功能、不将 world override 自动补全、不将重载迁移为后台线程。

## 可能的失败模式

- 测试环境可能缺少完整 Minecraft bootstrap 依赖；需要将业务逻辑保持在可通过临时 `ConfigFile` 和 package-private transaction seam 测试的边界内。
- 仅运行新测试不能替代完整服务器启动验证；最终仍需仓库所有者进行完整集成验证。
