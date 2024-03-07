<img src="leaf_logo.png" alt="Leaf logo" align="right" width="200">
<div align="center">

## Leaf

[![Github Actions Build](https://img.shields.io/github/actions/workflow/status/Winds-Studio/Leaf/build-1204.yml?&style=for-the-badge)](https://github.com/Winds-Studio/Leaf/releases)
[![Discord](https://img.shields.io/discord/1145991395388162119?color=5865F2&label=discord&style=for-the-badge)](https://discord.gg/gfgAwdSEuM)

<h5>Leaf is a drop-in replacement designed for removing some checks, customized, and high-performance built on top of <a href="https://github.com/GaleMC/Gale">Gale</a> with optimization from other forks.</h5>
<h8>Logo designed by <a href="https://github.com/envizar">envizar</a></h8>
</div>

## Features
 - **Fork of [Gale](https://github.com/GaleMC/Gale)** for better performance.
 - **Allows all characters as usernames**, including Chinese and other characters.
 - **Allows** players to connect the backend under proxy without enabling bunngecord mode.
 - **Allows tripwire dupe** by reverting tripwire bugfix patch of Paper.
 - **Configurable UseItem distance** for anarchy server.
 - **Latest dependencies**, keeping all dependencies in the newest version.
 - **More customized** relying on features of [Purpur](https://github.com/PurpurMC/Purpur).
 - **Maintenance friendly**, integrating with [Sentry](https://sentry.io/welcome/) of [Pufferfish](https://github.com/pufferfish-gg/Pufferfish) to easy track all errors coming from your server in excruciating detail.
 - **Various optimization** blending from [other forks](https://github.com/Winds-Studio/Leaf#credits).
 - **Better Region Format** Support for the Linear region file format from [LinearPurpur](https://github.com/StupidCraft/LinearPurpur)
 - ...

## Contact

- 📫 Discord: `dreeam___` | QQ: `2682173972`

## Downloads

The Reobf JAR can be obtained in the [Releases](https://github.com/Winds-Studio/Leaf/releases)

## Building

Building a Paperclip JAR for distribution:

```bash
./gradlew applyPatches && ./gradlew createReobfPaperclipJar
```

## API

Maven
```xml
<dependency>
    <groupId>org.dreeam.leaf</groupId>
    <artifactId>leaf-api</artifactId>
    <version>1.20.4-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

Gradle
```kotlin
dependencies {
    compileOnly("org.dreeam.leaf.:leaf-api:1.20.4-R0.1-SNAPSHOT")
}
```

## License

Paperweight files are licensed under MIT.
Patches are licensed under MIT, unless indicated differently in their header.
Binaries are licensed under GPL-3.0.

Also see [PaperMC/Paper](https://github.com/PaperMC/Paper) and [PaperMC/Paperweight](https://github.com/PaperMC/paperweight) for the license of some material used by this project.

Credits:
-------------
Thanks to these projects below. Leaf just mix some of their patches together. If these excellent projects haven't appeared, Leaf will not be great.

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

## Special Thanks To:

<a href="https://cloud.swordsman.com.cn/"><img src="JiankeServer.jpg" alt="Jianke Cloud Host" align="left" hspace="8"></a>
cloud of swordsman | 剑客云

If you want to find a cheaper, high performance, stable with lower latency, then cloud of swordsman is a good choice! Registers and purchases in [here](https://cloud.swordsman.com.cn/?i8ab42c).

如果你想找一个低价高性能, 低延迟的云服务商，剑客云是个不错的选择! 你可以在[这里](https://cloud.swordsman.com.cn/?i8ab42c)注册.
