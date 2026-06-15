<img src="public/image/leaf_banner.png" alt="Leaf">
<div align="center">

[![Download](https://img.shields.io/badge/releases-blue?label=download&style=for-the-badge&colorA=19201a&colorB=298046)](https://www.leafmc.one/download)⠀
[![Github Actions Build](https://img.shields.io/github/actions/workflow/status/Winds-Studio/Leaf/build-2612.yml?&style=for-the-badge&colorA=19201a&colorB=298046)](https://github.com/Winds-Studio/Leaf/actions)⠀
[![Discord](https://img.shields.io/discord/1145991395388162119?label=discord&style=for-the-badge&colorA=19201a&colorB=298046)](https://discord.gg/gfgAwdSEuM)
[![Docs](https://img.shields.io/badge/leafmc.one/docs/-blue?label=docs&style=for-the-badge&colorA=19201a&colorB=298046)](https://www.leafmc.one/docs/getting-started)

**Leaf** is a [Paper](https://papermc.io/) fork designed to be customizable and high-performance.
</div>

> [!WARNING]
> Leaf is a performance-oriented fork. Make sure to take backups **before** switching to it. Everyone is welcome to contribute optimizations or report issues to help us improve.

**English** | [中文](public/readme/README_CN.md)

## 🍃 Features
- **Based on [Paper](https://papermc.io/)** for generic performance and flexible API
- **Async** pathfinding, mob spawning and entity tracker
- **Various optimizations** blending from [other forks](#-credits) and our own
- **Fully compatible** with Spigot and Paper plugins
- **Latest dependencies**, keeping all dependencies up-to-date
- **Allows all characters in usernames**, including Chinese and other characters
- **Fixes** some Minecraft bugs
- **Mod Protocols** support
- **More customized** relying on features of [Purpur](https://github.com/PurpurMC/Purpur)
- **Linear region file format**, to save disk space
- **Maintenance friendly**, integrating with [Sentry](https://sentry.io/welcome/) of [Pufferfish](https://github.com/pufferfish-gg/Pufferfish) to easily track all errors coming from your server in extreme detail
- And more...

## 📈 bStats
[![bStats Graph Data](https://bstats.org/signatures/server-implementation/Leaf.svg)](https://bstats.org/plugin/server-implementation/Leaf)

## 📫 Contact
- Discord: [`https://discord.com/invite/gfgAwdSEuM`](https://discord.com/invite/gfgAwdSEuM)
- QQ Group: `619278377`

## 📫 Donation
If you love our work, feel free to donate via our [Open Collective](https://opencollective.com/Winds-Studio) or [Dreeam's AFDIAN](https://afdian.com/a/Dreeam) :)

## 📥 Download
Download Leaf from our [Website](https://www.leafmc.one/download) or get latest build in [GitHub Releases](https://github.com/Winds-Studio/Leaf/releases)

## 📄 Documentation
Documentation about how to use/configure Leaf: [Leaf Docs](https://www.leafmc.one/docs/getting-started)

## 📦 Building
Building a Paperclip JAR for distribution:
```bash
./gradlew applyAllPatches && ./gradlew createPaperclipJar
```


## 📦 API
<details>
<summary>Click to expand</summary>

### Gradle
```kotlin
repositories {
  maven {
    url = uri("https://maven.leafmc.one/snapshots/")
  }
}

dependencies {
    compileOnly("cn.dreeam.leaf:leaf-api:26.1.2.build.+")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}
```

### Maven
```xml
<repository>
    <id>leafmc</id>
    <url>https://maven.leafmc.one/snapshots/</url>
</repository>
```
```xml
<dependency>
    <groupId>cn.dreeam.leaf</groupId>
    <artifactId>leaf-api</artifactId>
    <version>[26.1.2.build,)</version>
    <scope>provided</scope>
</dependency>
```
</details>

## ⚖️ License
Leaf is licensed under various open source licenses from its upstream projects. See [LICENSE.md](LICENSE.md) for full details.

## 📜 Credits
Thanks to these projects below. Leaf includes some patches taken from them.<br>
If these excellent projects hadn't existed, Leaf wouldn't have become great.

- [Gale](https://github.com/Dreeam-qwq/Gale) ([Original Repo](https://github.com/GaleMC/Gale))
- [Pufferfish](https://github.com/pufferfish-gg/Pufferfish)
- [Purpur](https://github.com/PurpurMC/Purpur)
- <details>
    <summary>🍴 Expand to see forks that Leaf takes patches from.</summary>
    <p>
      • <a href="https://github.com/KeYiMC/KeYi">KeYi</a> (R.I.P.)
        <a href="https://github.com/MikuMC/KeYiBackup">(Backup)</a><br>
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
      • <a href="https://github.com/Tuinity/Moonrise">Moonrise</a> (during 1.21.1)<br> 
      • <a href="https://github.com/Samsuik/Sakura">Sakura</a><br> 
    </p>
</details>

## 🔥 Special Thanks

<table>
  <tr>
    <td width="50%" align="center">
      <a href="https://cloud.swordsman.com.cn/?i8ab42c">
        <img src="public/image/JiankeServer.jpg" alt="Jianke Cloud Host" width="250">
      </a>
      <br>
      <b>cloud of swordsman | 剑客云</b>
      <p>If you want to find a cheaper, high performance, stable, lower latency host, then cloud of swordsman is a good choice! Registers and purchases in <a href="https://cloud.swordsman.com.cn/?i8ab42c">here</a>.</p>
      <p>如果你想找一个低价高性能、低延迟的云服务商，剑客云是个不错的选择！你可以在 <a href="https://cloud.swordsman.com.cn/?i8ab42c">这里</a> 注册。</p>
    </td>
    <td width="50%" align="center">
      <a href="https://www.rainyun.com/NzE2NTc1_">
        <img src="public/image/RainYun.jpg" alt="雨云" width="250">
      </a>
      <br>
      <b>RainYun | 雨云</b>
      <p>Global multi-line routing with cloud storage. Refund available within 7 days. Reliable uptime and expert support. RainYun — stable, cost-effective, and ready for fast cloud deployment. Visit <a href="https://www.rainyun.com/NzE2NTc1_">RainYun</a>.</p>
      <p>国际多线路选择，配套云存储 — 购买服务后七天内不满意可以申请退订，强大的技术支持团队和高在线率客服。雨云云服务器，用稳定和性价比，助力您快速上云。点击前往 <a href="https://www.rainyun.com/NzE2NTc1_">雨云</a>。</p>
    </td>
  </tr>
  <tr>
    <td colspan="2" align="center">
      <a href="https://www.yourkit.com/">
        <img src="https://www.yourkit.com/images/yklogo.png" alt="YourKit" width="300">
      </a>
      <p>YourKit supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications. YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>, <a href="https://www.yourkit.com/dotnet-profiler/">YourKit .NET Profiler</a>, and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.</p>
    </td>
  </tr>
</table>