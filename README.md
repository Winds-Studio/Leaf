<img src="leaf_banner.png" alt="Leaf">
<div align="center">
 
[![Github Releases](https://img.shields.io/badge/Download-Releases-blue?&style=for-the-badge&colorA=19201a&colorB=298046)](https://github.com/Winds-Studio/Leaf/releases)⠀
[![Github Actions Build](https://img.shields.io/github/actions/workflow/status/Winds-Studio/Leaf/build-1204.yml?&style=for-the-badge&colorA=19201a&colorB=298046)](https://github.com/Winds-Studio/Leaf/actions)⠀
[![Discord](https://img.shields.io/discord/1145991395388162119?label=discord&style=for-the-badge&colorA=19201a&colorB=298046)](https://discord.gg/gfgAwdSEuM)

**Leaf** is a drop-in replacement for [Paper](https://papermc.io/) servers designed to remove some checks, customized and high-performance, built on top of [Gale](https://github.com/GaleMC/Gale) with optimizations and fixes from other forks.
</div>

## 🍃 Features
 - **Fork of [Gale](https://github.com/GaleMC/Gale)** for better performance
 - **Async** entity tracker, pathfinding and mob spawning
 - **Linear region support** from [LinearPurpur](https://github.com/StupidCraft/LinearPurpur) to save disk space
 - **Various optimizations** blending from [other forks](https://github.com/Winds-Studio/Leaf#-credits)
 - **Fully compatible** with Bukkit, Spigot and Paper plugins 
 - **Latest dependencies**, keeping all dependencies in the newest version
 - **Allows all characters in usernames**, including Chinese and other characters
 - **Ability to disable** useless console messages
 - **Fixes** some Minecraft bugs
 - **Allows** to connect the backend via a proxy server without enabling the bungeecord mode
 - **Configurable** tripwire dupe
 - **Configurable UseItem distance** for anarchy servers
 - **Mod Protocols** support
 - **More customized** relying on features of [Purpur](https://github.com/PurpurMC/Purpur)
 - **Maintenance friendly**, integrating with [Sentry](https://sentry.io/welcome/) of [Pufferfish](https://github.com/pufferfish-gg/Pufferfish) to easy track all errors coming from your server in excruciating detail

## 📈 bStats
[![bStats Graph Data](https://bstats.org/signatures/server-implementation/Leaf.svg)](https://bstats.org/plugin/server-implementation/Leaf)

## 📫 Contact
- Discord: [`https://discord.gg/gfgAwdSEuM`](https://discord.gg/gfgAwdSEuM)
- QQ: `2682173972`

## 📥 Download
You can find latest successful build in [GitHub Action](https://github.com/Winds-Studio/Leaf/actions) or [Releases](https://github.com/Winds-Studio/Leaf/releases)

**Please note Java >= 17 is required, Java >= 21 is recommended.**

## 📦 Building
Building a Paperclip JAR for distribution:
```bash
./gradlew applyPatches && ./gradlew createReobfPaperclipJar
```

## 🧪 API (WIP)

### Maven
```xml
<dependency>
    <groupId>org.dreeam.leaf</groupId>
    <artifactId>leaf-api</artifactId>
    <version>1.20.4-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```
### Gradle
```kotlin
dependencies {
    compileOnly("org.dreeam.leaf:leaf-api:1.20.4-R0.1-SNAPSHOT")
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
- [KeYi](https://github.com/KeYiMC/KeYi) (R.I.P.) [(Backup)](https://github.com/MikuMC/KeYiBackup)
- [KTP](https://github.com/lynxplay/ktp)
- [Mirai](https://github.com/etil2jz/Mirai)
- [Petal](https://github.com/Bloom-host/Petal)
- [Carpet Fixes](https://github.com/fxmorin/carpet-fixes)
- [Akarin](https://github.com/Akarin-project/Akarin)
- [Slice](https://github.com/Cryptite/Slice)
- [Parchment](https://github.com/ProjectEdenGG/Parchment)
- [Leaves](https://github.com/LeavesMC/Leaves)
- [Kaiiju](https://github.com/KaiijuMC/Kaiiju)
- [PandaSpigot](https://github.com/hpfxd/PandaSpigot)
- [Plazma](https://github.com/PlazmaMC/PlazmaBukkit)
- [SparklyPaper](https://github.com/SparklyPower/SparklyPaper)
- [Polpot](https://github.com/HaHaWTH/Polpot)
- [Matter](https://github.com/plasmoapp/matter)

## 🔥 Special Thanks
<a href="https://cloud.swordsman.com.cn/"><img src="JiankeServer.jpg" alt="Jianke Cloud Host" align="left" hspace="8"></a>
cloud of swordsman | 剑客云

If you want to find a cheaper, high performance, stable with lower latency, then cloud of swordsman is a good choice! Registers and purchases in [here](https://cloud.swordsman.com.cn/?i8ab42c).

如果你想找一个低价高性能, 低延迟的云服务商，剑客云是个不错的选择! 你可以在[这里](https://cloud.swordsman.com.cn/?i8ab42c)注册.
