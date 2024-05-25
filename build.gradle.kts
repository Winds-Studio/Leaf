import io.papermc.paperweight.util.constants.*

plugins {
    java
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
    id("io.github.goooler.shadow") version "8.1.7" apply false
    id("io.papermc.paperweight.patcher") version "1.5.9-SNAPSHOT"
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"

repositories {
    mavenCentral()
    maven(paperMavenPublicUrl) {
        content { onlyForConfigurations(configurations.paperclip.name) }
    }
    maven("https://maven.nostal.ink/repository/maven-snapshots/")
}

dependencies {
    remapper("net.fabricmc:tiny-remapper:0.8.9:fat")
    decompiler("org.vineflower:vineflower:1.9.3")
    paperclip("cn.dreeam:quantumleaper:1.0.0-SNAPSHOT")
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}

subprojects {
    tasks.withType<JavaCompile> {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(17)
    }
    tasks.withType<Javadoc> {
        options.encoding = Charsets.UTF_8.name()
    }
    tasks.withType<ProcessResources> {
        filteringCharset = Charsets.UTF_8.name()
    }

    repositories {
        mavenCentral()
        maven(paperMavenPublicUrl)
        maven("https://oss.sonatype.org/content/groups/public/")
        maven("https://ci.emc.gs/nexus/content/groups/aikar/")
        maven("https://repo.aikar.co/content/groups/aikar")
        maven("https://repo.md-5.net/content/repositories/releases/")
        maven("https://hub.spigotmc.org/nexus/content/groups/public/")
        maven("https://jitpack.io")
        maven("https://repo.codemc.io/repository/maven-public/")
    }
}

paperweight {
    serverProject.set(project(":leaf-server"))

    remapRepo.set("https://maven.fabricmc.net/")
    decompileRepo.set("https://maven.quiltmc.org/")

    useStandardUpstream("Gale") {
        url.set(github("Dreeam-qwq", "Gale"))
        ref.set(providers.gradleProperty("galeCommit"))

        withStandardPatcher {
            apiSourceDirPath.set("gale-api")
            serverSourceDirPath.set("gale-server")

            apiPatchDir.set(layout.projectDirectory.dir("patches/api"))
            apiOutputDir.set(layout.projectDirectory.dir("Leaf-API"))

            serverPatchDir.set(layout.projectDirectory.dir("patches/server"))
            serverOutputDir.set(layout.projectDirectory.dir("Leaf-Server"))
        }
    }
}