# AGENTS.md

## Project overview

Leaf is a high-performance Paper fork.

## Allowed scope

Unless the user explicitly expands the scope, only modify applied Java source
files in these areas:

- Minecraft sources under `leaf-server`
- Applied Paper API sources
- Applied Paper server sources
- Existing Leaf source files located alongside those applied sources

Before editing, locate the actual applied source file in the current working
tree. Follow the existing directory layout instead of assuming a source path.
If a required applied source file is missing, report the missing target and ask
the owner to prepare the source tree. Do not generate or apply sources, or edit
patches as a substitute.

Files outside these applied source areas are read-only unless the user
explicitly requests otherwise.

## Patch restrictions

Do not create, modify, delete, rename, regenerate, reformat, apply, rebuild,
reset, or export patch files or patch metadata, including through patch rebuild
or generation tasks.

This covers Paper, Minecraft, API, Leaf, upstream, and generated `.patch` files,
including patch metadata and ordering or series files. Reading them for review
and attribution is allowed.

Leaf patch directories (relative to the repository root; read-only):

- `leaf-server/minecraft-patches/`: Minecraft implementation changes.
- `leaf-server/paper-patches/`: Paper server implementation changes.
- `leaf-api/paper-patches/`: Paper API changes.

`leaf-archived-patches/` is historical reference, not the current version's
baseline.

Do not reset applied sources from patches or update Paper or Minecraft upstream
references. The repository owner converts source changes into patches when
necessary.

Applied source files are the editing target for the current task, even when
patch files are the canonical persisted form used by the project.

## CodeGraph usage

When a `.codegraph/` index exists and the tool is available, use CodeGraph for
tasks requiring cross-file or cross-module relationship analysis: call paths,
implementations, inheritance, overrides, field access, dependencies, or unclear
ownership, lifecycle, and threading relationships. Use `codegraph_explore` or
`codegraph explore "<symbols or question>"`.

For simple, local changes, inspect the current file and its immediate references
directly. CodeGraph is not a prerequisite for every edit.

Treat index results as navigation hints. Before changing behavior, inspect the
actual applied source and verify relevant signatures, callers, overrides, and
control flow. Use direct source search when the index or tool is unavailable,
returns no useful result, or conflicts with the checked-out code.

Do not update, rebuild, or reconfigure the CodeGraph index unless the user
explicitly requests it.

In the completion report, mention CodeGraph only when its analysis materially
affected the change or when the index appeared incomplete or stale.

## Editing workflow

1. Read the relevant applied source and its surrounding implementation.
2. Make the smallest changes needed, avoiding unrelated formatting or cleanup.
3. Review the resulting source diff for correctness.

## Validation policy

Stop after modifying and reviewing the applied source; the owner performs
compilation, testing, benchmarking, and runtime validation.

Do not run Gradle builds or compilation tasks, test suites, JMH benchmarks,
patch validation or rebuild tasks, server startup tasks, modifying formatters,
or scripts that generate or rewrite repository content.

Read-only inspection is allowed: locate files, search references, read source,
patches, configuration, and Gradle files, and inspect Git status and diffs.

Do not claim that a change compiles, passes tests, improves performance, or
works at runtime unless the user provides corresponding verification results.

## Java conventions

- Use the project's Java version and follow the surrounding Minecraft, Paper,
  or Leaf code style.
- Preserve nullability, visibility, annotations, and API contracts.
- Avoid introducing new dependencies.
- Preserve comments that explain upstream behavior or non-obvious invariants.

## Performance-sensitive code

Leaf contains performance-sensitive server code. When changing a hot path:

- avoid unnecessary allocation, boxing, copying, and temporary collections,
  including repeated construction of coordinates, positions, or keys;
- avoid streams and capturing lambdas when surrounding code uses explicit loops
  for performance;
- consider sparse, typical, and dense workloads;
- consider memory retention and backing-array capacity;
- preserve early exits and established fast paths;
- do not change observable vanilla or Paper behavior solely for performance.

Explain the expected effect of an optimization and distinguish it from measured
results.

## Threading and lifecycle

Do not assume that code is safe to run asynchronously.

Before changing thread ownership or asynchronous behavior, inspect mutable
state, tick-thread or region-thread assumptions, world and chunk lifecycle,
entity addition and removal, shutdown and unload behavior, synchronization and
publication, and interactions with plugins and Paper APIs.

Do not move work to another thread, introduce concurrency, or weaken an
existing thread check unless the user explicitly requests it and the safety
argument is clear.

## Compatibility

Unless the user explicitly requests a behavioral change, preserve vanilla and
Paper API behavior, plugin compatibility, serialized and persistent formats,
world loading and upgrading, configuration defaults, and public and internal
API contracts.

For API changes, consider both the applied Paper API source and its server-side
implementation.

## Generated code

The applied source tree designated in Allowed scope is an authorized editing
target even when produced by patch application. Other generated files are
read-only unless the user explicitly identifies them as the editing target.

If a source file appears to be generated, copied, or overwritten by a build or
patch task, report that fact before relying on the change as persistent.

## Git safety

Preserve all unrelated working-tree changes.

Before editing, inspect the relevant files and use `git status` when available.
Do not assume existing changes were produced by an agent.

Do not run `git reset`, `git checkout --`, `git restore`, `git clean`,
`git rebase`, `git commit`, or `git push`, or discard, overwrite, stage, or revert
user changes, unless the user explicitly requests that exact action.

## Review expectations

When reviewing or changing applied source, prioritize behavioral correctness,
vanilla and Paper compatibility, thread safety and lifecycle correctness,
hot-path allocation and computational cost, memory retention, API compatibility,
and diff clarity, in that order.

Separate confirmed defects from possible risks and optional optimizations.
Assess confidence independently from issue attribution.

For each potential defect, read the relevant Leaf patch diffs and compare the
corresponding Paper baseline with the current applied source. Account for earlier
patches affecting the same behavior: a patch's removed lines may already include
Leaf changes. When patch context is insufficient, inspect the upstream source
at the `paperCommit` revision in `gradle.properties`, if available. Do not assume
a different or latest Paper version is equivalent; mark attribution uncertain
when the necessary baseline evidence is unavailable.

For each reported defect or risk, put a separate, bold attribution field directly
below its title, before the explanation: `**Attribution: <label>** — <evidence>`.
Use a self-explanatory label in the report's language corresponding to:

- `Leaf-introduced` / `Leaf-worsened`: introduced or worsened by Leaf.
- `Leaf-exposed`: a pre-existing defect made reachable by Leaf.
- `Upstream`: inherited from Paper or vanilla.
- `Unsupported usage`: caused by usage outside the supported contract.
- `Unknown`: attribution is not established; identify the missing evidence.

The attribution line must give a brief reason, not just a label. Do not bury it
in the explanation.
Then explain the trigger, impact, Paper baseline behavior, and supporting source
or patch references. Attribution does not replace the full analysis.

Leaf exposing a pre-existing issue does not establish that the original usage
or behavior was valid. Support any claim of unsupported usage with the relevant
API contract, thread restriction, or other evidence; otherwise mark it uncertain.

Judge severity by impact, separately from Leaf's action priority. Upstream
issues or unsupported usage normally have lower priority for Leaf action and
belong in supplemental findings, not among confirmed Leaf regressions. If Leaf
expands the affected scenarios or worsens the consequences, assess that change
separately. Do not omit a serious impact solely because its origin is upstream.

## Completion report

At the end of a task, report:

- which files changed;
- what behavior changed;
- important threading, compatibility, or performance considerations;
- concrete scenarios and expected outcomes still requiring manual verification.
