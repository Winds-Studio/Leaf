# AGENTS.md

## Project overview

Leaf is a high-performance Paper fork.

For tasks performed in this repository, work only on the applied source tree.
The repository owner will manually inspect, test, and convert changes into
patches when necessary.

## Allowed scope

Unless the user explicitly expands the scope, only modify applied Java source
files in these areas:

- Minecraft sources under `leaf-server`
- Applied Paper API sources
- Applied Paper server sources
- Existing Leaf source files located alongside those applied sources

Before editing, locate the actual applied source file in the current working
tree. Follow the existing directory layout instead of assuming a source path.

Files outside these applied source areas are read-only unless the user
explicitly requests otherwise.

## Patch restrictions

Do not create, modify, delete, rename, regenerate, or reformat patch files.

This includes, but is not limited to:

- Paper patches
- Minecraft patches
- API patches
- Leaf patches
- Upstream patches
- Generated `.patch` files
- Patch metadata
- Patch ordering or series files

Do not run tasks or scripts that apply, rebuild, regenerate, reset, export, or
otherwise modify patches.

In particular, do not:

- run patch rebuild tasks;
- run patch generation tasks;
- edit `.patch` files directly;
- convert source changes into patches;
- update an existing patch to include source changes;
- reset applied sources from patches;
- update Paper or Minecraft upstream references.

Applied source files are the editing target for the current task, even when
patch files are the canonical persisted form used by the project.

## CodeGraph usage

CodeGraph is available for repository-wide code navigation and dependency
analysis.

Use CodeGraph when the task requires understanding relationships across
multiple files, including:

- callers and callees;
- interface implementations;
- class inheritance;
- field reads and writes;
- method overrides;
- dependency paths;
- cross-module relationships between Minecraft, Paper API, Paper server, and
  Leaf code;
- ownership, lifecycle, or threading relationships that are not clear from the
  current file.

Prefer direct source inspection and text search for simple, local changes.
Do not use CodeGraph when reading the current file and its immediate references
is sufficient.

CodeGraph is an index and may be incomplete or stale. Treat its results as
navigation hints rather than authoritative source code.

Before editing:

1. Use CodeGraph to identify relevant symbols and relationships when the task
   crosses files or modules.
2. Open and inspect the actual applied source files returned by the query.
3. Verify important callers, overrides, signatures, and control flow against
   the current working tree.
4. Search the source directly when CodeGraph returns no result or conflicts
   with the checked-out code.

Only analyze applied source code in the allowed scope:

- Minecraft sources under `leaf-server`;
- applied Paper API sources;
- applied Paper server sources;
- existing Leaf source files alongside those sources.

Do not use CodeGraph results as a reason to modify patch files, generated
patches, upstream metadata, or files outside the allowed editing scope.

Do not update, rebuild, or reconfigure the CodeGraph index unless the user
explicitly requests it.

In the completion report, mention CodeGraph only when its analysis materially
affected the change or when the index appeared incomplete or stale.

## Editing workflow

1. Read the relevant applied source and its surrounding implementation.
2. For cross-file or cross-module behavior, use CodeGraph to locate callers,
   callees, implementations, overrides, and state access.
3. Verify relevant CodeGraph results against the actual checked-out source.
4. Identify ownership, lifecycle, and threading assumptions when they affect
   the requested change.
5. Modify only the applied source files required for the task.
6. Keep the diff focused and avoid unrelated formatting or cleanup.
7. Review the resulting source diff for correctness.
8. Report the changed files and any assumptions or risks.

Stop after modifying and reviewing the applied source. Leave patch creation,
patch rebuilding, compilation, testing, benchmarking, and runtime validation
to the repository owner.

## Validation policy

The repository owner manually validates changes.

Do not run:

- Gradle build or compilation tasks;
- test suites;
- JMH benchmarks;
- patch validation or rebuild tasks;
- server startup tasks;
- formatters that modify files;
- scripts that generate or rewrite repository content.

Read-only inspection commands are allowed, including:

- locating source files;
- searching references and call sites;
- reading source and configuration;
- inspecting `git status`;
- inspecting diffs;
- viewing Gradle files to understand dependencies or source layout.

Do not claim that a change compiles, passes tests, improves performance, or
works at runtime unless the user provides corresponding verification results.

## Java conventions

- Use the Java version and language style already established by the project.
- Follow the style of the surrounding Minecraft, Paper, or Leaf code.
- Prefer minimal and locally consistent changes.
- Preserve nullability, visibility, annotations, and API contracts.
- Avoid introducing new dependencies.
- Avoid unrelated refactors unless they are necessary for the requested change.
- Do not reformat surrounding code merely to match personal preferences.
- Preserve comments that explain upstream behavior or non-obvious invariants.

## Performance-sensitive code

Leaf contains performance-sensitive server code. When changing a hot path:

- avoid unnecessary allocation, boxing, copying, and temporary collections;
- avoid streams and capturing lambdas when surrounding code uses explicit loops
  for performance;
- avoid repeated object construction for coordinates, positions, or keys;
- consider sparse, typical, and dense workloads;
- consider memory retention and backing-array capacity;
- preserve early exits and established fast paths;
- distinguish measured improvements from speculative micro-optimizations;
- do not change observable vanilla or Paper behavior solely for performance.

When proposing a performance optimization, explain its expected effect without
claiming benchmark results that were not measured.

## Threading and lifecycle

Do not assume that code is safe to run asynchronously.

Before changing thread ownership or asynchronous behavior, inspect:

- mutable state accessed by the code;
- tick-thread or region-thread assumptions;
- world and chunk lifecycle;
- entity addition and removal;
- shutdown and unload behavior;
- synchronization and publication;
- interaction with plugins and Paper APIs.

Do not move work to another thread, introduce concurrency, or weaken an
existing thread check unless the user explicitly requests it and the safety
argument is clear.

## Compatibility

Preserve the following unless the user explicitly requests a behavioral change:

- vanilla behavior;
- Paper API behavior;
- plugin compatibility;
- serialized and persistent data formats;
- world loading and upgrading behavior;
- existing configuration defaults;
- public and internal API contracts.

For API changes, consider both the applied Paper API source and its server-side
implementation, but modify only the files needed for the requested task.

## Generated code

Do not modify generated files unless the user explicitly identifies the
generated file as the desired editing target.

If a source file appears to be generated, copied, or overwritten by a build or
patch task, report that fact before relying on the change as persistent.

Do not run a generator to update it.

## Git safety

Preserve all unrelated working-tree changes.

Before editing, inspect the relevant files and use `git status` when available.
Do not assume existing changes were produced by Codex.

Never run destructive or history-changing commands, including:

- `git reset`;
- `git checkout --`;
- `git restore`;
- `git clean`;
- `git rebase`;
- `git commit`;
- `git push`.

Do not discard, overwrite, stage, commit, or revert user changes unless the
user explicitly requests that exact action.

## Review expectations

When reviewing or changing applied source, prioritize:

1. behavioral correctness;
2. vanilla and Paper compatibility;
3. thread safety and lifecycle correctness;
4. hot-path allocation and computational cost;
5. memory retention;
6. API compatibility;
7. clarity of the resulting source diff.

Separate confirmed defects from possible risks and optional optimizations.

## Completion report

At the end of a task, report:

- which applied source files changed;
- what behavior changed;
- important threading, compatibility, or performance considerations;
- anything that still requires manual verification.

Do not report patch files, generated patches, build results, test results, or
benchmark results unless the user separately supplied or requested them.
