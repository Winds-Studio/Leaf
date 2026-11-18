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

Locate the actual applied source file in the working tree before editing. All
other files are read-only unless the user explicitly requests otherwise.

## Patch system

Leaf uses Paper's paperweight patch system; follow the workflow in
`CONTRIBUTING.md`. Apply, create, and update patches only through its Git
workflow and Gradle tasks. Never edit `.patch` files or their numbering or
ordering directly.

Patch directories:

- `leaf-server/minecraft-patches/`: Minecraft implementation changes.
- `leaf-server/paper-patches/`: Paper server implementation changes.
- `leaf-api/paper-patches/`: Paper API changes.

`leaf-archived-patches/` is historical reference, not the current baseline.

Applying patches overwrites applied sources; make sure no unsaved changes
would be lost first. Do not update upstream references (such as `paperCommit`)
unless asked.

## CodeGraph usage

When a `.codegraph/` index exists, use `codegraph_explore` or
`codegraph explore "<symbols or question>"` for cross-file analysis: call paths,
implementations, overrides, field access, ownership, lifecycle, and threading.
For local changes, read the file and its immediate references directly.

Treat results as navigation hints; verify against the actual source before
changing behavior. Fall back to direct search if the index is unavailable,
unhelpful, or stale. Do not update or rebuild the index unless asked.

## Validation policy

The owner performs compilation, testing, benchmarking, and runtime validation.

Do not run Gradle builds, compilation, tests, JMH benchmarks, server startup,
modifying formatters, or scripts that rewrite repository content. Patch system
tasks are the exception.

Do not claim that a change compiles, passes tests, improves performance, or
works at runtime without user-provided verification results.

## Java conventions

- Follow the surrounding Minecraft, Paper, or Leaf code style.
- Make the smallest change needed; avoid unrelated formatting or cleanup.
- Avoid introducing new dependencies.
- Preserve comments that explain upstream behavior or non-obvious invariants.

## Performance-sensitive code

When changing a hot path:

- avoid unnecessary allocation, boxing, copying, and temporary collections,
  including repeated construction of positions or keys;
- avoid streams and capturing lambdas where surrounding code uses explicit
  loops;
- consider sparse, typical, and dense workloads, and memory retention;
- preserve early exits and established fast paths;
- do not change observable vanilla or Paper behavior solely for performance.

Explain the expected effect of an optimization, distinct from measured results.

## Threading and lifecycle

Do not move work to another thread, introduce concurrency, or weaken a thread
check unless the user explicitly requests it and the safety argument is clear.
Before doing so, inspect mutable state, tick/region-thread assumptions, world,
chunk and entity lifecycle, shutdown/unload, publication, and plugin and Paper
API interactions.

## Compatibility

Unless a behavioral change is explicitly requested, preserve vanilla and Paper
API behavior, plugin compatibility, nullability, visibility, annotations,
serialized and persistent formats, world loading and upgrading, and
configuration defaults. For API changes, update both the Paper API source and
its server implementation.

## Git safety

Preserve all unrelated working-tree changes; do not assume existing changes
were produced by an agent.

Git operations in the applied source repositories required by the patch
workflow are allowed; stage only your own changes. Otherwise, do not run `git reset`, `git checkout --`, `git restore`,
`git clean`, `git rebase`, `git commit`, or `git push`, or stage, discard, or
revert user changes, unless the user explicitly requests it. Never commit in
the root repository unless asked.

## Review expectations

Prioritize, in order: behavioral correctness, vanilla and Paper compatibility,
thread safety and lifecycle, hot-path cost, memory retention, API
compatibility, and diff clarity.

Separate confirmed defects from possible risks and optional optimizations.
Assess confidence independently from attribution.

For each potential defect, read the relevant Leaf patch diffs and compare the
Paper baseline with the current applied source. Account for earlier patches
affecting the same behavior. When patch context is insufficient, inspect
upstream at the `paperCommit` in `gradle.properties`; do not assume another
Paper version is equivalent.

Directly below each reported defect or risk title, add a bold attribution line
before the explanation: `**Attribution: <label>** — <brief reason>`. Use a
self-explanatory label in the report's language for:

- `Leaf-introduced` / `Leaf-worsened`: introduced or worsened by Leaf.
- `Leaf-exposed`: a pre-existing defect made reachable by Leaf.
- `Upstream`: inherited from Paper or vanilla.
- `Unsupported usage`: usage outside the supported contract (cite the
  contract, thread restriction, or other evidence; otherwise mark uncertain).
- `Unknown`: attribution not established; name the missing evidence.

Then explain the trigger, impact, Paper baseline behavior, and source or patch
references.

Judge severity by impact, separately from Leaf's action priority. Upstream
issues and unsupported usage belong in supplemental findings, not among
confirmed Leaf regressions; assess separately if Leaf expands or worsens them.
Do not omit a serious impact solely because its origin is upstream.

## Completion report

Report:

- files changed;
- behavior changed;
- important threading, compatibility, or performance considerations;
- concrete scenarios and expected outcomes still requiring manual verification.
