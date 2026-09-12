import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

// Edit the rebase list in Git's configured sequence.editor/core.editor (use a GUI editor with --wait in an IDE).
// Mark a feature commit as edit, amend it with commit*FeatureChanges, then run continueRebasing.
// If multiple repositories are rebasing, select one with -PpatchTarget=minecraft, -PpatchTarget=paper, or -PpatchTarget=api.
@DisableCachingByDefault(because = "Interactively modifies applied feature patch history")
abstract class FeaturePatchEditingTask : DefaultTask() {
    @get:Input
    abstract val operation: Property<String>

    @get:Input
    abstract val target: Property<String>

    @get:Internal
    abstract val repositories: MapProperty<String, File>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun editPatches() {
        val availableRepositories = repositories.get()
        val selectedTarget = target.get()
        val repository = if (selectedTarget.isNotEmpty()) {
            availableRepositories[selectedTarget]
                ?: throw GradleException("Unknown patch target '$selectedTarget'; use ${availableRepositories.keys.joinToString(", ")}.")
        } else {
            val rebasing = availableRepositories.values.filter { it.resolve(".git").exists() && isRebasing(it) }
            when (rebasing.size) {
                0 -> throw GradleException("No applied repository is rebasing.")
                1 -> rebasing.single()
                else -> throw GradleException("Multiple repositories are rebasing. Select one with ${availableRepositories.keys.joinToString(" or ") { "-PpatchTarget=$it" }}.")
            }
        }

        // Do not let Git walk up into the main repository when applied sources are missing
        if (!repository.resolve(".git").exists()) {
            throw GradleException("Applied Git repository is missing: $repository. Apply the patches before editing feature patches.")
        }

        when (operation.get()) {
            "edit" -> {
                if (isRebasing(repository)) {
                    throw GradleException("$repository is already rebasing. Finish or abort that rebase first.")
                }
                if (gitOutput(repository, "status", "--porcelain", "--untracked-files=normal").isNotEmpty()) {
                    throw GradleException("$repository has uncommitted changes. Commit or stash them before editing feature patches.")
                }
                // Paperweight uses 'file' as the boundary between file patches and feature commits.
                runGit(repository, "merge-base", "--is-ancestor", "refs/tags/file", "HEAD")
                runGit(repository, "rebase", "--interactive", "--no-autostash", "--keep-empty", "--empty=keep", "refs/tags/file")
                if (isRebasing(repository)) {
                    logger.lifecycle("Rebase paused in {}. Edit the sources, run the matching commit*FeatureChanges task, then continueRebasing.", repository)
                }
            }
            "commit" -> {
                // 'amend' alone can also exist after a failed squash/fixup/reword.
                val rebaseDirectory = gitDirectory(repository).resolve("rebase-merge")
                val amend = rebaseDirectory.resolve("amend")
                val done = rebaseDirectory.resolve("done")
                val lastCommand = if (done.isFile) {
                    done.readLines().map { it.trim() }.lastOrNull { it.isNotEmpty() && !it.startsWith("#") }
                        ?.takeWhile { !it.isWhitespace() }
                } else null
                if (!amend.isFile || lastCommand !in setOf("edit", "e")) {
                    throw GradleException("$repository is not paused at an interactive rebase edit step. Resolve pick conflicts and use continueRebasing instead.")
                }
                // Allow repeated amendments, but not a manually advanced or reset HEAD.
                val expectedParent = gitOutput(repository, "rev-parse", "${amend.readText().trim()}^")
                if (gitOutput(repository, "rev-parse", "HEAD^") != expectedParent) {
                    throw GradleException("HEAD no longer represents the feature commit being edited in $repository.")
                }
                if (gitOutput(repository, "ls-files", "--unmerged").isNotEmpty()) {
                    throw GradleException("$repository has unresolved conflicts. Resolve and stage them before continuing the rebase.")
                }
                if (gitOutput(repository, "status", "--porcelain", "--untracked-files=normal").isEmpty()) {
                    logger.lifecycle("No changes to amend in {}. Run continueRebasing to proceed.", repository)
                    return
                }
                runGit(repository, "add", "--all")
                runGit(repository, "commit", "--amend", "--no-edit")
                logger.lifecycle("Amended the current feature patch in {}. Run continueRebasing to proceed.", repository)
            }
            "continue" -> {
                if (!isRebasing(repository)) {
                    throw GradleException("$repository is not rebasing.")
                }
                runGit(repository, "rebase", "--continue")
                logger.lifecycle(if (isRebasing(repository)) "Rebase paused in {}." else "Rebase completed in {}.", repository)
            }
            else -> throw GradleException("Unknown patch editing operation: ${operation.get()}")
        }
    }

    private fun gitDirectory(repository: File): File = File(gitOutput(repository, "rev-parse", "--absolute-git-dir"))

    private fun isRebasing(repository: File): Boolean {
        val directory = gitDirectory(repository)
        return directory.resolve("rebase-merge").isDirectory || directory.resolve("rebase-apply").isDirectory
    }

    private fun gitOutput(repository: File, vararg arguments: String): String {
        val output = ByteArrayOutputStream()
        execOperations.exec {
            workingDir(repository)
            commandLine(listOf("git") + arguments)
            standardOutput = output
        }.assertNormalExitValue()
        return output.toString(Charsets.UTF_8).trim()
    }

    private fun runGit(repository: File, vararg arguments: String) {
        execOperations.exec {
            workingDir(repository)
            commandLine(listOf("git") + arguments)
            standardInput = System.`in`
        }.assertNormalExitValue()
    }
}

val appliedRepositories = mapOf(
    "minecraft" to rootProject.file("leaf-server/src/minecraft/java"),
    "paper" to rootProject.file("paper-server"),
    "api" to rootProject.file("paper-api"),
)

tasks.register<FeaturePatchEditingTask>("editMinecraftFeaturePatches") {
    description = "Open the interactive rebase list for Minecraft feature patches, mark commits as edit."
    operation.set("edit")
    target.set("minecraft")
}

tasks.register<FeaturePatchEditingTask>("editPaperFeaturePatches") {
    description = "Open the interactive rebase list for Paper feature patches, mark commits as edit."
    operation.set("edit")
    target.set("paper")
}

tasks.register<FeaturePatchEditingTask>("editApiFeaturePatches") {
    description = "Open the interactive rebase list for API feature patches, mark commits as edit."
    operation.set("edit")
    target.set("api")
}

tasks.register<FeaturePatchEditingTask>("commitMinecraftFeatureChanges") {
    description = "Stage all Minecraft changes and amend the feature commit at the current rebase edit stop."
    operation.set("commit")
    target.set("minecraft")
}

tasks.register<FeaturePatchEditingTask>("commitPaperFeatureChanges") {
    description = "Stage all Paper changes and amend the feature commit at the current rebase edit stop."
    operation.set("commit")
    target.set("paper")
}

tasks.register<FeaturePatchEditingTask>("commitApiFeatureChanges") {
    description = "Stage all API changes and amend the feature commit at the current rebase edit stop."
    operation.set("commit")
    target.set("api")
}

tasks.register<FeaturePatchEditingTask>("continueRebasing") {
    description = "Continue the active rebase after edits or staged conflict resolutions: use -PpatchTarget if multiple repositories are rebasing."
    operation.set("continue")
    target.set(providers.gradleProperty("patchTarget").orElse(""))
}

tasks.withType<FeaturePatchEditingTask>().configureEach {
    group = "patch editing"
    repositories.set(appliedRepositories)
}
