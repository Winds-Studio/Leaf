plugins {
    java
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "7.1.2" apply false
    id("io.papermc.paperweight.patcher") version "1.3.9"
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"

repositories {
    mavenCentral()
    maven(paperMavenPublicUrl) {
        content { onlyForConfigurations(configurations.paperclip.name) }
    }
}

dependencies {
    remapper("net.fabricmc:tiny-remapper:0.8.6:fat")
    decompiler("org.quiltmc:quiltflower:1.8.1")
    paperclip("io.papermc:paperclip:3.0.3-SNAPSHOT")
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

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

    useStandardUpstream("mirai") {
        url.set(github("etil2jz", "Mirai"))
        ref.set(providers.gradleProperty("MiraiCommit"))

        withStandardPatcher {
            //thanks Pearl
            apiSourceDirPath.set("mirai-api")
            serverSourceDirPath.set("mirai-server")

            apiPatchDir.set(layout.projectDirectory.dir("patches/api"))
            apiOutputDir.set(layout.projectDirectory.dir("Leaf-API"))

            serverPatchDir.set(layout.projectDirectory.dir("patches/server"))
            serverOutputDir.set(layout.projectDirectory.dir("Leaf-Server"))
        }
    }
}