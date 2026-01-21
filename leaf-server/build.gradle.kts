import io.papermc.fill.model.BuildChannel
import io.papermc.paperweight.attribute.DevBundleOutput
import io.papermc.paperweight.util.*
import java.time.Instant

plugins {
    `java-library`
    `maven-publish`
    idea
    id("io.papermc.paperweight.core")
    id("io.papermc.fill.gradle") version "1.0.10"
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"
val leafMavenPublicUrl = "https://maven.leafmc.one/snapshots/" // Leaf - project setup - Add publish repo

dependencies {
    mache("io.papermc:mache:1.21.11+build.1")
    paperclip("cn.dreeam:quantumleaper:1.0.0-SNAPSHOT") // Leaf - project setup - Use own paperclip fork
}

paperweight {
    minecraftVersion = providers.gradleProperty("mcVersion")
    gitFilePatches = false

    // Leaf start - project setup
    val leaf = forks.register("leaf") {
        upstream.patchDir("paperServer") {
            upstreamPath = "paper-server"
            excludes = setOf("src/minecraft", "patches", "build.gradle.kts")
            patchesDir = rootDirectory.dir("leaf-server/paper-patches")
            outputDir = rootDirectory.dir("paper-server")
        }
    }

    activeFork = leaf
    // Leaf end - project setup

    spigot {
        enabled = true
        buildDataRef = "17f77cee7117ab9d6175f088ae8962bfd04e61a9"
        packageVersion = "v1_21_R7" // also needs to be updated in MappingEnvironment
    }

    reobfPackagesToFix.addAll(
        "co.aikar.timings",
        "com.destroystokyo.paper",
        "com.mojang",
        "io.papermc.paper",
        "ca.spottedleaf",
        "net.kyori.adventure.bossbar",
        "net.minecraft",
        "org.bukkit.craftbukkit",
        "org.spigotmc",
    )

    updatingMinecraft {
        // oldPaperCommit = "c82b438b5b4ea0b230439b8e690e34708cd11ab3"
    }
}

tasks.generateDevelopmentBundle {
    libraryRepositories.addAll(
        "https://repo.maven.apache.org/maven2/",
        paperMavenPublicUrl,
        leafMavenPublicUrl // Leaf - project setup - Add publish repo
    )
}

abstract class Services {
    @get:Inject
    abstract val archiveOperations: ArchiveOperations
}
val services = objects.newInstance<Services>()

if (project.providers.gradleProperty("publishDevBundle").isPresent) {
    val devBundleComponent = publishing.softwareComponentFactory.adhoc("devBundle")
    components.add(devBundleComponent)

    val devBundle = configurations.consumable("devBundle") {
        attributes.attribute(DevBundleOutput.ATTRIBUTE, objects.named(DevBundleOutput.ZIP))
        outgoing.artifact(tasks.generateDevelopmentBundle.flatMap { it.devBundleFile })
    }
    devBundleComponent.addVariantsFromConfiguration(devBundle) {}

    val runtime = configurations.consumable("serverRuntimeClasspath") {
        attributes.attribute(DevBundleOutput.ATTRIBUTE, objects.named(DevBundleOutput.SERVER_DEPENDENCIES))
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        extendsFrom(configurations.runtimeClasspath.get())
    }
    devBundleComponent.addVariantsFromConfiguration(runtime) {
        mapToMavenScope("runtime")
    }

    val compile = configurations.consumable("serverCompileClasspath") {
        attributes.attribute(DevBundleOutput.ATTRIBUTE, objects.named(DevBundleOutput.SERVER_DEPENDENCIES))
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
        extendsFrom(configurations.compileClasspath.get())
    }
    devBundleComponent.addVariantsFromConfiguration(compile) {
        mapToMavenScope("compile")
    }

    tasks.withType(GenerateMavenPom::class).configureEach {
        doLast {
            val text = destination.readText()
            // Remove dependencies from pom, dev bundle is designed for gradle module metadata consumers
            destination.writeText(
                text.substringBefore("<dependencies>") + text.substringAfter("</dependencies>")
            )
        }
    }

    publishing {
        publications.create<MavenPublication>("devBundle") {
            artifactId = "dev-bundle"
            from(devBundleComponent)
        }
    }
}

// Leaf start - project setup
sourceSets {
    main {
        java {
            srcDir("../paper-server/src/main/java")
            srcDir("../JUring/src/main/java")
            exclude("module-info.java")
        }
        resources { srcDir("../paper-server/src/main/resources") }
    }
    test {
        java { srcDir("../paper-server/src/test/java") }
        resources { srcDir("../paper-server/src/test/resources") }
    }
}

val log4jPlugins = sourceSets.create("log4jPlugins") {
    java { srcDir("../paper-server/src/log4jPlugins/java") }
}
// Leaf end - project setup
configurations.named(log4jPlugins.compileClasspathConfigurationName) {
    extendsFrom(configurations.compileClasspath.get())
}
val alsoShade: Configuration by configurations.creating

val runtimeConfiguration by configurations.consumable("runtimeConfiguration") {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    extendsFrom(configurations.getByName(sourceSets.main.get().runtimeElementsConfigurationName))
}

// Configure mockito agent that is needed in newer java versions
val mockitoAgent = configurations.register("mockitoAgent")
abstract class MockitoAgentProvider : CommandLineArgumentProvider {
    @get:CompileClasspath
    abstract val fileCollection: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> {
        return listOf("-javaagent:" + fileCollection.files.single().absolutePath)
    }
}

dependencies {
    implementation(project(":leaf-api")) // Leaf - project setup

    // Leaf start - Libraries
    implementation("com.github.thatsmusic99:ConfigurationMaster-API:v2.0.0-rc.3") { // Leaf config
        exclude(group = "org.yaml", module = "snakeyaml")
    }
    //implementation("com.github.luben:zstd-jni:1.5.7-6") // LinearPaper // TODO - move to buffered linear
    //implementation("org.lz4:lz4-java:1.8.0") // LinearPaper // TODO - move to buffered linear
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")
    // Leaf end - Libraries

    implementation("ca.spottedleaf:concurrentutil:0.0.8")
    implementation("org.jline:jline-terminal-ffm:3.29.0") // use ffm on java 22+ // Leaf - Bump Dependencies // Waiting a fix for jline3#1445
    implementation("org.jline:jline-terminal-jni:3.29.0") // fall back to jni on java 21 // Leaf - Bump Dependencies // Waiting a fix for jline3#1445
    implementation("net.minecrell:terminalconsoleappender:1.3.0")
    implementation("net.kyori:adventure-text-serializer-ansi")

    /*
      Required to add the missing Log4j2Plugins.dat file from log4j-core
      which has been removed by Mojang. Without it, log4j has to classload
      all its classes to check if they are plugins.
      Scanning takes about 1-2 seconds so adding this speeds up the server start.
     */
    // Leaf start - Bump Dependencies
    implementation("org.apache.logging.log4j:log4j-core:2.25.3")
    log4jPlugins.annotationProcessorConfigurationName("org.apache.logging.log4j:log4j-core:2.25.3") // Needed to generate meta for our Log4j plugins
    // Leaf end - Bump Dependencies
    runtimeOnly(log4jPlugins.output)
    alsoShade(log4jPlugins.output)

    implementation("com.velocitypowered:velocity-native:3.4.0-SNAPSHOT") {
        isTransitive = false
    }
    // Leaf start - Bump Dependencies
    implementation("io.netty:netty-codec-haproxy:4.2.9.Final") // Add support for proxy protocol
    implementation("org.apache.logging.log4j:log4j-iostreams:2.25.3")
    implementation("org.ow2.asm:asm-commons:9.9")
    // Leaf end - Bump Dependencies
    implementation("org.spongepowered:configurate-yaml:4.2.0")

    // Purpur start
    implementation("org.mozilla:rhino-runtime:1.7.15")
    implementation("org.mozilla:rhino-engine:1.7.15")
    implementation("dev.omega24:upnp4j:1.0")
    // Purpur end

    // Deps that were previously in the API but have now been moved here for backwards compat, eventually to be removed
    runtimeOnly("commons-lang:commons-lang:2.6")
    // Leaf start - Bump Dependencies
    runtimeOnly("org.xerial:sqlite-jdbc:3.51.1.0")
    runtimeOnly("com.mysql:mysql-connector-j:9.5.0") {
        exclude("com.google.protobuf", "protobuf-java") // Leaf - Exclude outdated protobuf version
    }
    runtimeOnly("com.google.protobuf:protobuf-java:4.33.4")
    runtimeOnly("com.lmax:disruptor:3.4.4") // Dreeam TODO - Waiting Log4j 3.x to support disruptor 4.0.0
    // Leaf end - Bump Dependencies
    implementation("com.googlecode.json-simple:json-simple:1.1.1") { // change to runtimeOnly once Timings is removed
        isTransitive = false // includes junit
    }

    // Leaf start - Bump Dependencies
    testImplementation("io.github.classgraph:classgraph:4.8.184") // For mob goal test
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.0-M1")
    testImplementation("org.junit.platform:junit-platform-suite-engine:6.1.0-M1")
    testImplementation("org.hamcrest:hamcrest:3.0")
    testImplementation("org.mockito:mockito-core:5.21.0")
    mockitoAgent("org.mockito:mockito-core:5.21.0") { isTransitive = false } // Configure mockito agent that is needed in newer java versions
    testImplementation("org.ow2.asm:asm-tree:9.9")
    testImplementation("org.junit-pioneer:junit-pioneer:2.3.0") // CartesianTest
    // Leaf end - Bump Dependencies

    implementation("net.neoforged:srgutils:1.0.9") // Mappings handling
    implementation("net.neoforged:AutoRenamingTool:2.0.3") // Remap plugins

    // Remap reflection
    val reflectionRewriterVersion = "0.0.3"
    implementation("io.papermc:reflection-rewriter:$reflectionRewriterVersion")
    implementation("io.papermc:reflection-rewriter-runtime:$reflectionRewriterVersion")
    implementation("io.papermc:reflection-rewriter-proxy-generator:$reflectionRewriterVersion")

    // Leaf start - Bump Dependencies
    // Spark
    implementation("me.lucko:spark-api:0.1-dev-7e3a173-SNAPSHOT")
    implementation("me.lucko:spark-paper:1.10-dev-7e3a173-SNAPSHOT")

    implementation("io.netty:netty-all:4.2.9.Final")
    // Leaf end - Bump Dependencies
}

// Gale start - hide irrelevant compilation warnings
tasks.withType<JavaCompile> {
    val compilerArgs = options.compilerArgs
    compilerArgs.add("-Xlint:-module")
    compilerArgs.add("-Xlint:-removal")
    compilerArgs.add("-Xlint:-dep-ann")
    compilerArgs.add("--add-modules=jdk.incubator.vector") // Gale - Pufferfish - SIMD support
}
// Gale end - hide irrelevant compilation warnings

tasks.jar {
    manifest {
        val git = Git(rootProject.layout.projectDirectory.path)
        val mcVersion = rootProject.providers.gradleProperty("mcVersion").get()
        val build = System.getenv("BUILD_NUMBER") ?: null
        val buildTime = Instant.now() // Leaf - project setup - Always use current as build time
        val gitHash = git.exec(providers, "rev-parse", "--short=7", "HEAD").get().trim()
        val implementationVersion = "$mcVersion-${build ?: "DEV"}-$gitHash"
        val date = git.exec(providers, "show", "-s", "--format=%ci", gitHash).get().trim()
        val gitBranch = git.exec(providers, "rev-parse", "--abbrev-ref", "HEAD").get().trim()
        attributes(
            "Main-Class" to "org.bukkit.craftbukkit.Main",
            "Implementation-Title" to "Leaf", // Leaf - Rebrand
            "Implementation-Version" to implementationVersion,
            "Implementation-Vendor" to date,
            "Specification-Title" to "Leaf", // Leaf - Rebrand
            "Specification-Version" to project.version,
            "Specification-Vendor" to "Winds Studio", // Leaf - Rebrand
            "Brand-Id" to "winds-studio:leaf", // Leaf - Rebrand
            "Brand-Name" to "Leaf", // Leaf - Rebrand
            "Build-Number" to (build ?: ""),
            "Build-Time" to buildTime.toString(),
            "Git-Branch" to gitBranch,
            "Git-Commit" to gitHash,
        )
        for (tld in setOf("net", "com", "org")) {
            attributes("$tld/bukkit", "Sealed" to true)
        }
    }
}

// Compile tests with -parameters for better junit parameterized test names
tasks.compileTestJava {
    options.compilerArgs.add("-parameters")
}

// Bump compile tasks to 1GB memory to avoid OOMs
tasks.withType<JavaCompile>().configureEach {
    options.forkOptions.memoryMaximumSize = "1G"
}

val scanJarForBadCalls by tasks.registering(io.papermc.paperweight.tasks.ScanJarForBadCalls::class) {
    badAnnotations.add("Lio/papermc/paper/annotation/DoNotUse;")
    jarToScan.set(tasks.jar.flatMap { it.archiveFile })
    classpath.from(configurations.compileClasspath)
}
tasks.check {
    dependsOn(scanJarForBadCalls)
}

// Use TCA for console improvements
tasks.jar {
    val archiveOperations = services.archiveOperations
    from(alsoShade.elements.map {
        it.map { f ->
            if (f.asFile.isFile) {
                archiveOperations.zipTree(f.asFile)
            } else {
                f.asFile
            }
        }
    })
}

tasks.test {
    include("**/**TestSuite.class")
    workingDir = temporaryDir
    useJUnitPlatform {
        forkEvery = 1
        excludeTags("Slow")
    }

    // Configure mockito agent that is needed in newer java versions
    val provider = objects.newInstance<MockitoAgentProvider>()
    provider.fileCollection.from(mockitoAgent)
    jvmArgumentProviders.add(provider)
}

val generatedDir: java.nio.file.Path = rootProject.layout.projectDirectory.dir("paper-server/src/generated/java").asFile.toPath() // Leaf - project setup
idea {
    module {
        generatedSourceDirs.add(generatedDir.toFile())
    }
}
sourceSets {
    main {
        java {
            srcDir(generatedDir)
        }
    }
}

fun TaskContainer.registerRunTask(
    name: String,
    block: JavaExec.() -> Unit
): TaskProvider<JavaExec> = register<JavaExec>(name) {
    group = "runs"
    mainClass.set("org.bukkit.craftbukkit.Main")
    standardInput = System.`in`
    workingDir = rootProject.layout.projectDirectory
        .dir(providers.gradleProperty("paper.runWorkDir").getOrElse("run"))
        .asFile
    javaLauncher.set(project.javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.JETBRAINS)
    })
    jvmArgs("-XX:+AllowEnhancedClassRedefinition")

    if (rootProject.childProjects["test-plugin"] != null) {
        val testPluginJar = rootProject.project(":test-plugin").tasks.jar.flatMap { it.archiveFile }
        inputs.file(testPluginJar)
        args("-add-plugin=${testPluginJar.get().asFile.absolutePath}")
    }

    args("--nogui")
    systemProperty("net.kyori.adventure.text.warnWhenLegacyFormattingDetected", true)
    if (providers.gradleProperty("paper.runDisableWatchdog").getOrElse("false") == "true") {
        systemProperty("disable.watchdog", true)
    }
    systemProperty("io.papermc.paper.suppress.sout.nags", true)

    val memoryGb = providers.gradleProperty("paper.runMemoryGb").getOrElse("2")
    minHeapSize = "${memoryGb}G"
    maxHeapSize = "${memoryGb}G"
    jvmArgs("--enable-preview") // Gale - enable preview features for development runs
    jvmArgs("--add-modules=jdk.incubator.vector") // Gale - Pufferfish - SIMD support

    doFirst {
        workingDir.mkdirs()
    }

    block(this)
}

tasks.registerRunTask("runServer") {
    description = "Spin up a test server from the Mojang mapped server jar"
    classpath(tasks.includeMappings.flatMap { it.outputJar })
    classpath(configurations.runtimeClasspath)
}

tasks.registerRunTask("runReobfServer") {
    description = "Spin up a test server from the reobfJar output jar"
    classpath(tasks.reobfJar.flatMap { it.outputJar })
    classpath(configurations.runtimeClasspath)
}

tasks.registerRunTask("runDevServer") {
    description = "Spin up a test server without assembling a jar"
    classpath(sourceSets.main.map { it.runtimeClasspath })
}

tasks.registerRunTask("runBundler") {
    description = "Spin up a test server from the Mojang mapped bundler jar"
    classpath(tasks.createMojmapBundlerJar.flatMap { it.outputZip })
    mainClass.set(null as String?)
}
tasks.registerRunTask("runReobfBundler") {
    description = "Spin up a test server from the reobf bundler jar"
    classpath(tasks.createReobfBundlerJar.flatMap { it.outputZip })
    mainClass.set(null as String?)
}
tasks.registerRunTask("runPaperclip") {
    description = "Spin up a test server from the Mojang mapped Paperclip jar"
    classpath(tasks.createMojmapPaperclipJar.flatMap { it.outputZip })
    mainClass.set(null as String?)
}
tasks.registerRunTask("runReobfPaperclip") {
    description = "Spin up a test server from the reobf Paperclip jar"
    classpath(tasks.createReobfPaperclipJar.flatMap { it.outputZip })
    mainClass.set(null as String?)
}

fill {
    project("paper")
    versionFamily(paperweight.minecraftVersion.map { it.split(".", "-").takeWhile { part -> part.toIntOrNull() != null }.take(2).joinToString(".") })
    version(paperweight.minecraftVersion)

    build {
        channel = BuildChannel.STABLE

        downloads {
            register("server:default") {
                file = tasks.createMojmapPaperclipJar.flatMap { it.outputZip }
                nameResolver.set { project, _, version, build -> "$project-$version-$build.jar" }
            }
        }
    }
}

// Gale start - package license into jar
tasks.register<Copy>("copyLicense") {
    from(layout.projectDirectory.file("../paper-server/LICENSE.txt"))
    into(layout.buildDirectory.dir("tmp/copiedlicense"))
}

tasks.processResources {
    dependsOn("copyLicense")
}

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("tmp/copiedlicense"))
        }
    }
}
// Gale end - package license into jar

// Gale start - branding changes - package license into jar
// Based on io.papermc.paperweight.core.taskcontainers.PaperclipTasks
tasks.named("createMojmapPaperclipJar") {
    val name = rootProject.name
    val version = project.version
    val licenseFileName = "LICENSE.txt"
    val licenseFilePath = layout.projectDirectory.dir("../paper-server/$licenseFileName").asFile

    // Based on io.papermc.paperweight.core.taskcontainers.PaperclipTasks
    val jarName = listOfNotNull(
        name,
        "paperclip",
        version,
        "mojmap"
    ).joinToString("-") + ".jar"

    // Based on io.papermc.paperweight.core.taskcontainers.PaperclipTasks
    val zipFile = layout.buildDirectory.file("libs/$jarName").get().path

    val rootDir = findOutputDir(zipFile)

    doLast {

        try {
            unzip(zipFile, rootDir)

            licenseFilePath.copyTo(rootDir.resolve(licenseFileName).toFile())

            ensureDeleted(zipFile)

            zip(rootDir, zipFile)
        } finally {
            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            rootDir.toFile().deleteRecursively()
        }

    }
}
// Gale end - branding changes - package license into jar

// Leaf start - Leaf JUnit test suite
tasks.register<Test>("runLeafTests") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    include("**/LeafTestSuite.class")
    workingDir = temporaryDir
    useJUnitPlatform {
        forkEvery = 1
    }

    // Configure mockito agent that is needed in newer java versions
    val provider = objects.newInstance<MockitoAgentProvider>()
    provider.fileCollection.from(mockitoAgent)
    jvmArgumentProviders.add(provider)
}
// Leaf end - Leaf JUnit test suite
