<img src="leaf_banner.png" alt="Leaf">
<div align="center">
 
[![Github Releases](https://img.shields.io/badge/Download-Releases-blue?&style=for-the-badge&colorA=19201a&colorB=298046)](https://github.com/Winds-Studio/Leaf/releases)⠀
[![Github Actions Build](https://img.shields.io/github/actions/workflow/status/Winds-Studio/Leaf/build-1206.yml?&style=for-the-badge&colorA=19201a&colorB=298046)](https://github.com/Winds-Studio/Leaf/actions)⠀
[![Discord](https://img.shields.io/discord/1145991395388162119?label=discord&style=for-the-badge&colorA=19201a&colorB=298046)](https://discord.gg/gfgAwdSEuM)

**Leaf** is a drop-in replacement for [Paper](https://papermc.io/) servers designed to remove some checks, customized and high-performance, built on top of [Gale](https://github.com/GaleMC/Gale) with optimizations and fixes from other forks.
</div>

## 🍃 Features
 - **Fork of [Gale](https://github.com/GaleMC/Gale)** for better performance
 - **Async** pathfinding, mob spawning and entity tracker
 - **Various optimizations** blending from [other forks](https://github.com/Winds-Studio/Leaf#-credits)
 - **Fully compatible** with Bukkit, Spigot and Paper plugins 
 - **Latest dependencies**, keeping all dependencies in the newest version
 - **Allows all characters in usernames**, including Chinese and other characters
 - **Ability to disable** useless console messages
 - **Fixes** some Minecraft bugs
 - **Allows** to connect the backend via a proxy server without enabling the bungeecord mode
 - **Configurable UseItem distance** for anarchy servers
 - **Mod Protocols** support
 - **More customized** relying on features of [Purpur](https://github.com/PurpurMC/Purpur)
 - **Maintenance friendly**, integrating with [Sentry](https://sentry.io/welcome/) of [Pufferfish](https://github.com/pufferfish-gg/Pufferfish) to easy track all errors coming from your server in excruciating detail
 - And more...

## 📈 bStats
[![bStats Graph Data](https://bstats.org/signatures/server-implementation/Leaf.svg)](https://bstats.org/plugin/server-implementation/Leaf)

## 📫 Contact
- Discord: [`https://discord.gg/gfgAwdSEuM`](https://discord.gg/gfgAwdSEuM)
- QQ: `2682173972`

## 📫 Donation
If you love my work, feel free to donate :)
- Ko-Fi: 

## 📥 Download
You can find latest successful build in [GitHub Action](https://github.com/Winds-Studio/Leaf/actions) or [Releases](https://github.com/Winds-Studio/Leaf/releases)

**Please note Java >= 21 is required.**

## 📦 Building
Building a Paperclip JAR for distribution:
```bash
./gradlew applyPatches && ./gradlew createMojmapPaperclipJar
```

## 🧪 API (WIP)

### Maven
```xml
<dependency>
    <groupId>cn.dreeam.leaf</groupId>
    <artifactId>leaf-api</artifactId>
    <version>1.20.6-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```
### Gradle
```kotlin
dependencies {
    compileOnly("cn.dreeam.leaf:leaf-api:1.20.6-R0.1-SNAPSHOT")
}
```

## ⚖️ License
Paperweight files are licensed under MIT.
Patches are licensed under MIT, unless indicated differently in their header.
Binaries are licensed under GPL-3.0.

Also see [PaperMC/Paper](https://github.com/PaperMC/Paper) and [PaperMC/Paperweight](https://github.com/PaperMC/paperweight) for the license of some material used by this project.

## 📜 Credits
Thanks to these projects below. Leaf just mix some of their patches together.<br>
If these excellent projects hadn't appeared, Leaf wouldn't have become great.

- [Gale](https://github.com/GaleMC/Gale)
- [Pufferfish](https://github.com/pufferfish-gg/Pufferfish)
- [Purpur](https://github.com/PurpurMC/Purpur)
- <details>
    <summary>🍴 Expand to see forks that Leaf takes patches from.</summary>
    <p>
      • <a href="https://github.com/KeYiMC/KeYi">KeYi</a> (R.I.P.)
        <a href="https://github.com/MikuMC/KeYiBackup">(Backup)</a><br>
      • <a href="https://github.com/lynxplay/ktp">KTP</a><br>
      • <a href="https://github.com/etil2jz/Mirai">Mirai</a><br>
      • <a href="https://github.com/Bloom-host/Petal">Petal</a><br>
      • <a href="https://github.com/fxmorin/carpet-fixes">Carpet Fixes</a><br>
      • <a href="https://github.com/Akarin-project/Akarin">Akarin</a><br>
      • <a href="https://github.com/Cryptite/Slice">Slice</a><br>
      • <a href="https://github.com/ProjectEdenGG/Parchment">Parchment</a><br>
      • <a href="https://github.com/LeavesMC/Leaves">Leaves</a><br>
      • <a href="https://github.com/KaiijuMC/Kaiiju">Kaiiju</a><br>
      • <a href="https://github.com/hpfxd/PandaSpigot">PandaSpigot</a><br>
      • <a href="https://github.com/PlazmaMC/PlazmaBukkit">Plazma</a><br>
      • <a href="https://github.com/SparklyPower/SparklyPaper">SparklyPaper</a><br>
      • <a href="https://github.com/HaHaWTH/Polpot">Polpot</a><br>
      • <a href="https://github.com/plasmoapp/matter">Matter</a><br>
      • <a href="https://github.com/LuminolMC/Luminol">Luminol</a><br>
    </p>
</details>

## 🔥 Special Thanks
<a href="https://cloud.swordsman.com.cn/"><img src="JiankeServer.jpg" alt="Jianke Cloud Host" align="left" hspace="8"></a>
cloud of swordsman | 剑客云

If you want to find a cheaper, high performance, stable with lower latency, then cloud of swordsman is a good choice! Registers and purchases in [here](https://cloud.swordsman.com.cn/?i8ab42c).

如果你想找一个低价高性能, 低延迟的云服务商，剑客云是个不错的选择! 你可以在[这里](https://cloud.swordsman.com.cn/?i8ab42c)注册.

---
![YourKit](https://www.yourkit.com/images/yklogo.png)

YourKit supports open source projects with innovative and intelligent tools 
for monitoring and profiling Java and .NET applications.
YourKit is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/),
[YourKit .NET Profiler](https://www.yourkit.com/dotnet-profiler/),
and [YourKit YouMonitor](https://www.yourkit.com/youmonitor/).
