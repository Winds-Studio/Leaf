<div align="center">

## Leaf

[![MIT License](https://img.shields.io/github/license/PurpurMC/Purpur?&logo=github)](LICENSE)
[![Github Actions Build](https://img.shields.io/github/workflow/status/PurpurMC/purpur/Build?event=push&logo=github)](https://purpurmc.org/downloads/)
[![Join us on Discord](https://img.shields.io/discord/685683385313919172.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://purpurmc.org/discord)

Leaf is a drop-in replacement for [Purpur](https://github.com/PurpurMC/Purpur) servers designed for fix some bugs and customize, and performance built on top of [Purpur](https://github.com/PurpurMC/Purpur).

</div>

## Contact
[![Join us on Discord](https://img.shields.io/discord/685683385313919172.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/mtAAnkk)

Join us on [Discord](https://discord.gg/mtAAnkk)

## Downloads

Downloads can be obtained from the [downloads page](https://purpurmc.org/downloads/) or the [downloads API](https://api.purpurmc.org).


## License
[![MIT License](https://img.shields.io/github/license/PurpurMC/Purpur?&logo=github)](LICENSE)

All patches are licensed under the MIT license, unless otherwise noted in the patch headers.

See [PaperMC/Paper](https://github.com/PaperMC/Paper), and [PaperMC/Paperweight](https://github.com/PaperMC/paperweight) for the license of material used by this project.


## API

### [Javadoc](https://purpurmc.org/javadoc)

### Dependency Information
Maven
```xml
<repository>
    <id>purpur</id>
    <url>https://repo.purpurmc.org/snapshots</url>
</repository>
```
```xml
<dependency>
    <groupId>org.purpurmc.purpur</groupId>
    <artifactId>purpur-api</artifactId>
    <version>1.19-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

Gradle
```kotlin
repositories {
    maven("https://repo.purpurmc.org/snapshots")
}
```
```kotlin
dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:1.19-R0.1-SNAPSHOT")
}
```

Yes, this also includes all API provided by Paper, Spigot, and Bukkit.

## Building and setting up

#### Initial setup
Run the following command in the root directory:

```
./gradlew applyPatches
```

#### Creating a patch
Patches are effectively just commits in either `Purpur-API` or `Purpur-Server`. 
To create one, just add a commit to either repo and run `./gradlew rebuildPatches`, and a 
patch will be placed in the patches folder. Modifying commits will also modify its 
corresponding patch file.

See [CONTRIBUTING.md](CONTRIBUTING.md) for more detailed information.


#### Compiling

Use the command `./gradlew build` to build the API and server. Compiled JARs
will be placed under `Purpur-API/build/libs` and `Purpur-Server/build/libs`.

To get a purpurclip jar, run `./gradlew createReobfPaperclipJar`.
To install the `purpur-api` and `purpur` dependencies to your local Maven repo, run `./gradlew publishToMavenLocal`

Special Thanks To:
-------------

<a href="https://purpurmc.org"><img src="https://user-images.githubusercontent.com/74448585/150906023-101cd383-da82-4a3c-9603-a3b5741c3994.png" alt="Purpur"></a>

