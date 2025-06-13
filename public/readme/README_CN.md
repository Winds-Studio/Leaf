<img src="../image/leaf_banner.png" alt="Leaf">
<div align="center">

[![下载](https://img.shields.io/badge/releases-blue?label=%e4%b8%8b%e8%bd%bd&style=for-the-badge&colorA=19201a&colorB=298046)](https://www.leafmc.one/zh/download)⠀
[![Github Actions 构建](https://img.shields.io/github/actions/workflow/status/Winds-Studio/Leaf/build-1214.yml?label=%e6%9e%84%e5%bb%ba&style=for-the-badge&colorA=19201a&colorB=298046)](https://github.com/Winds-Studio/Leaf/actions)⠀
![QQ](https://img.shields.io/badge/619278377-blue?label=QQ%e7%be%a4&style=for-the-badge&colorA=19201a&colorB=298046)
[![文档](https://img.shields.io/badge/leafmc.one/zh/docs-blue?label=%e6%96%87%e6%a1%a3&style=for-the-badge&colorA=19201a&colorB=298046)](https://www.leafmc.one/zh/docs)

**Leaf** 是一个基于 [Paper](https://papermc.io/) 的分支，专为高自定义和高性能而设计，基于 [Gale](https://github.com/Dreeam-qwq/Gale) 之上，并融合了其他核心的优化和修复。
</div>

> [!WARNING]
> Leaf 是一个面向性能的分支。在迁移到 Leaf 之前，请务必**提前备份**。欢迎任何人贡献优化或报告问题来帮助我们改进。

[English](../../README.md) | **中文**

## 🍃 特点
- **基于 [Gale](https://github.com/Dreeam-qwq/Gale)**，以获得更好的性能
- **异步**寻路、生物生成和实体追踪
- **大量优化**融合自 [其他核心](#-致谢) 和我们自己的的补丁
- **完全兼容** Spigot 和 Paper 插件
- **最新依赖**，保持所有依赖项为最新版本
- **允许用户名使用所有字符**，包括中文和其他字符
- **修复**一些 Minecraft 的 bug
- **模组协议**支持
- **更多自定义配置项**，源自 [Purpur](https://github.com/PurpurMC/Purpur) 的特性
- **线性区域文件格式**，节省磁盘空间
- **运维友好**，集成 [Pufferfish](https://github.com/pufferfish-gg/Pufferfish) 的 [Sentry](https://sentry.io/welcome/)，轻松详细追踪服务器的所有报错
- 以及更多...

## 📈 bStats 统计
[![bStats Graph Data](https://bstats.org/signatures/server-implementation/Leaf.svg)](https://bstats.org/plugin/server-implementation/Leaf)

## 📫 联系方式
- Discord: [`https://discord.com/invite/gfgAwdSEuM`](https://discord.com/invite/gfgAwdSEuM)
- QQ社区群: `619278377`

## 📫 赞助
如果您喜欢我们的工作，欢迎通过我们的 [Open Collective](https://opencollective.com/Winds-Studio) 或 [Dreeam 的爱发电](https://afdian.com/a/Dreeam) 进行赞助 :)

## 📥 下载
从我们的 [官网](https://www.leafmc.one/zh/download) 下载 Leaf，或在 [GitHub Action](https://github.com/Winds-Studio/Leaf/actions) 获取最新构建版本

**请注意需要 Java 21 以上。**

## 📄 文档
关于如何使用/配置 Leaf 的文档：[www.leafmc.one/zh/docs](https://www.leafmc.one/zh/docs)

## 📦 构建
构建用于分发的 Paperclip JAR：
```bash
./gradlew applyAllPatches && ./gradlew createMojmapPaperclipJar
```

## 🧪 API

### Maven
```xml
<repository>
    <id>leafmc</id>
    <url>https://maven.nostal.ink/repository/maven-snapshots/</url>
</repository>
```
```xml
<dependency>
    <groupId>cn.dreeam.leaf</groupId>
    <artifactId>leaf-api</artifactId>
    <version>1.21.4-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```
### Gradle
```kotlin
repositories {
  maven {
    url = uri("https://maven.nostal.ink/repository/maven-snapshots/")
  }
}

dependencies {
    compileOnly("cn.dreeam.leaf:leaf-api:1.21.4-R0.1-SNAPSHOT")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
```

## ⚖️ 许可证
Leaf 根据其上游项目及其他材料，采用多种开源许可证授权，

请参阅 [LICENSE.md](../../LICENSE.md) 获取完整的许可信息。

## 📜 致谢
感谢以下项目。Leaf 包含了一些取自这些项目的补丁。<br>
如果没有这些优秀的项目，Leaf 就不会变得如此出色。

- [Gale](https://github.com/Dreeam-qwq/Gale) ([原始仓库](https://github.com/GaleMC/Gale))
- [Pufferfish](https://github.com/pufferfish-gg/Pufferfish)
- [Purpur](https://github.com/PurpurMC/Purpur)
- <details>
    <summary>🍴 展开查看 Leaf 采用补丁的核心</summary>
    <p>
      • <a href="https://github.com/KeYiMC/KeYi">KeYi</a> (R.I.P.)
        <a href="https://github.com/MikuMC/KeYiBackup">(备份仓库)</a><br>
      • <a href="https://github.com/etil2jz/Mirai">Mirai</a><br>
      • <a href="https://github.com/Bloom-host/Petal">Petal</a><br>
      • <a href="https://github.com/fxmorin/carpet-fixes">Carpet Fixes</a><br>
      • <a href="https://github.com/Akarin-project/Akarin">Akarin</a><br>
      • <a href="https://github.com/Cryptite/Slice">Slice</a><br>
      • <a href="https://github.com/ProjectEdenGG/Parchment">Parchment</a><br>
      • <a href="https://github.com/LeavesMC/Leaves">Leaves</a><br>
      • <a href="https://github.com/KaiijuMC/Kaiiju">Kaiiju</a><br>
      • <a href="https://github.com/PlazmaMC/PlazmaBukkit">Plazma</a><br>
      • <a href="https://github.com/SparklyPower/SparklyPaper">SparklyPaper</a><br>
      • <a href="https://github.com/HaHaWTH/Polpot">Polpot</a><br>
      • <a href="https://github.com/plasmoapp/matter">Matter</a><br>
      • <a href="https://github.com/LuminolMC/Luminol">Luminol</a><br>
      • <a href="https://github.com/Gensokyo-Reimagined/Nitori">Nitori</a><br>
      • <a href="https://github.com/Tuinity/Moonrise">Moonrise</a> (在 1.21.1 期间)<br> 
      • <a href="https://github.com/Samsuik/Sakura">Sakura</a><br> 
    </p>
</details>

## 🔥 特别感谢
<a href="https://cloud.swordsman.com.cn/"><img src="../image/JiankeServer.jpg" alt="Jianke Cloud Host" align="left" hspace="8"></a>
剑客云 | cloud of swordsman

如果你想找一个低价高性能、低延迟的云服务商，剑客云是个不错的选择！你可以在 [这里](https://cloud.swordsman.com.cn/?i8ab42c) 注册。

If you want to find a cheaper, high performance, stable, lower latency host, then cloud of swordsman is a good choice! Registers and purchases in [here](https://cloud.swordsman.com.cn/?i8ab42c).

---
![YourKit](https://www.yourkit.com/images/yklogo.png)

YourKit 通过创新和智能的工具支持开源项目，用于监控和分析 Java 和 .NET 应用程序。
YourKit 是 [YourKit Java Profiler](https://www.yourkit.com/java/profiler/)、
[YourKit .NET Profiler](https://www.yourkit.com/dotnet-profiler/) 和
[YourKit YouMonitor](https://www.yourkit.com/youmonitor/) 的创造者。