<img src="Leaf.png" alt="Leaf logo" align="right" width="200">
<div align="center">

## Leaf

[![Github Actions Build](https://img.shields.io/github/actions/workflow/status/Winds-Studio/Leaf/build-1201.yml?branch=ver%2F1.20.1&style=flat-square)](https://github.com/Winds-Studio/Leaf/releases)
[![MIT License](https://img.shields.io/github/license/Winds-Studio/Leaf?style=flat-square)](LICENSE)

<h5>Leaf is a drop-in replacement designed for removing some checks, customized, and high-performance built on top of <a href="https://github.com/GaleMC/Gale">Gale</a> with optimization from other forks.</h5>
<h8>Logo designed by <a href="https://www.bing.com/new">New Bing</a></h8>
</div>

## Features
 - **Fork of [Gale](https://github.com/GaleMC/Gale)** for better performance.
 - **Allows all characters as usernames**, including Chinese and other characters.
 - **Allows** players to connect the backend under proxy without enabling bunngecord mode.
 - **Allows tripwire dupe** by reverting tripwire bugfix patch of Paper.
 - **Configurable UseItem distance** for anarchy server.
 - **Latest dependencies**, keeping all dependencies in the newest version.
 - **More customized** relying on features of [Purpur](https://github.com/PurpurMC/Purpur).
 - **Maintenance friendly**, integrating with [Sentry](https://sentry.io/welcome/) of [Pufferfish](https://github.com/pufferfish-gg/Pufferfish/blob/ver/1.19/patches/server/0005-Add-Sentry.patch) to easy track all errors coming from your server in excruciating detail.
 - **Various optimization** blending from [other forks](https://github.com/Winds-Studio/Leaf#credits).
 - ...

## Contact

- 📫 Discord: `dreeam___` | QQ: `2682173972`


## Downloads

The Reobf JAR can be obtained in the [Actions](https://github.com/Winds-Studio/Leaf/actions) or [Releases](https://github.com/Winds-Studio/Leaf/releases)


## Building

Building a Paperclip JAR for distribution:

```bash
./gradlew applyPatches && ./gradlew createReobfPaperclipJar
```


## License
[![MIT License](https://img.shields.io/github/license/Winds-Studio/Leaf?style=flat-square)](LICENSE)

All patches are licensed under the MIT license, unless otherwise noted in the patch headers.

See [PaperMC/Paper](https://github.com/PaperMC/Paper), and [PaperMC/Paperweight](https://github.com/PaperMC/paperweight) for the license of material used by this project.

Yes, this also includes all API provided by Paper, Spigot, and Bukkit.


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
- [Slice](https://github.com/Cryptite/Slice)
- [Parchment](https://github.com/ProjectEdenGG/Parchment)
- [Leaves](https://github.com/LeavesMC/Leaves)
- [Kaiiju](https://github.com/KaiijuMC/Kaiiju)
