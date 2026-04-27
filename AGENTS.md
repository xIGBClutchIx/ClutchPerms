# AGENTS.md

## Working Defaults

- Make reasonable assumptions and proceed unless the task is genuinely blocked.
- Keep changes scoped to the user request. Do not bundle unrelated refactors into feature work.
- Never revert unrelated local changes. Treat unexpected edits as user work unless proven otherwise.
- Use the Gradle wrapper: `./gradlew`.
- Run relevant checks before finishing and report exactly what ran.
- `.codex` is a local workspace file and is ignored. Do not stage it unless explicitly requested.
- NeoForge verification tasks clean generated `neoforge/logs/`; remove that directory before finishing only if it still appears.

## Project Purpose

ClutchPerms is an early cross-platform Minecraft permissions prototype for Paper, Fabric, NeoForge, and Forge. Keep it moving toward a useful permissions system without adding mature-suite complexity too early.

Current boundaries:

- shared permission, group, subject metadata, storage, audit, runtime, and command code lives in `common`
- platform modules should stay thin and adapt lifecycle/source/runtime APIs only
- SQLite-backed persisted state uses `config.json` plus `database.db`
- shared Brigadier `/clutchperms` commands are the main control surface
- Paper, Fabric, Forge, and NeoForge bridges resolve effective permissions on demand or refresh platform state from shared services

Do not add contexts, priorities, imports, migrations, or LuckPerms bridges unless the user asks for that slice.

## Repository Layout

- `common` - pure Java shared code. No Bukkit, Paper, Fabric, NeoForge, Forge, or Minecraft dependencies.
- `paper` - Paper plugin adapter. Paper-only target; Spigot compatibility is not maintained.
- `fabric` - Fabric mod adapter built with Loom.
- `neoforge` - NeoForge mod adapter built with ModDevGradle.
- `forge` - Forge mod adapter built with ForgeGradle.
- `docs` - user/admin reference docs. Keep long command and storage references here instead of bloating root docs.

Shared package ownership:

- `common.permission` - direct permissions, node normalization, wildcard matching, cached effective resolution, observers, and service factories
- `common.group` - group definitions, group permissions, memberships, parent links, group storage, and group observers
- `common.display` - ampersand-formatted prefix/suffix parsing and effective display resolution
- `common.node` - known permission node registry, manual node storage, registry composition, and node observers
- `common.subject` - last-known subject metadata and direct subject display values
- `common.config` - runtime config parsing, defaults, validation, and materialization
- `common.storage` - storage exceptions, atomic file writes, backup listing, and restore rollback helpers
- `common.audit` - command-layer audit entries, SQLite audit history storage, retention pruning, and in-memory test storage
- `common.runtime` - platform-neutral storage paths, active service snapshots, reload/validate/backup wiring, scheduled backups, resolver cache invalidation, and runtime refresh hooks
- `common.command` - shared Brigadier root wiring, command behavior, command messages, and platform command environment contract
- `common.command.subcommand` - shared Brigadier branch builders for `/backup`, `/user`, `/group`, `/users`, and `/nodes`

## Functional Guardrails

- Permission nodes normalize with `trim().toLowerCase(Locale.ROOT)`.
- Valid assignments are exact nodes, `*`, or terminal wildcards ending in `.*`; mid-node wildcards are invalid.
- Stored permission values are `TRUE`, `FALSE`, or absent. `PermissionService#hasPermission(...)` is true only for `TRUE`.
- Unknown permission nodes remain assignable; do not make assignment mutation depend on the known-node registry.
- Known-node registry entries must be exact nodes. Platform-discovered nodes are runtime-only and must not be written back to storage.
- The `default` group always exists, applies implicitly to every subject, and cannot be deleted, renamed, or assigned explicitly.
- The `op` group always exists, has no members by default, grants `* = TRUE`, applies only to explicit members, and is protected from definition/permission/display/parent edits.
- User group membership is direct only. Groups may recursively inherit parent groups.
- Effective resolution order is direct user assignment, explicit user group hierarchy, then implicit `default` hierarchy.
- Closer child group permissions beat parent permissions; at the same inheritance depth, `FALSE` wins over `TRUE`.
- Subject and group display text is ampersand-formatted only. Reject raw section signs, invalid codes, blank values, and raw values over 128 characters.
- Command targets resolve exact online name first, exact stored last-known name second, and UUID third. Ambiguous stored last-known names must fail clearly.

## Storage And Runtime

- Treat missing database storage as empty state with built-in `default` and `op` groups. Treat missing `config.json` as default config.
- After successful startup or reload, materialize missing `database.db` and `config.json`.
- Keep the config schema strict and deterministic. Do not silently start with empty state after a bad persisted file.
- Save mutations immediately with transactional SQLite writes.
- If a mutation transaction fails, keep live database, in-memory state, resolver notifications, and runtime bridge notifications unchanged.
- Reload and restore must be atomic from the command perspective: failed reload keeps the old config, services, resolver, and resolver cache.
- `/clutchperms validate` parses config and database storage without replacing active services/config, refreshing runtime bridges, or mutating storage.
- Backup restore validates before replacing live storage, closes the active SQLite pool, removes stale WAL/SHM files, reloads config plus database storage, and rolls disk back if reload fails.
- Audit is command-layer only. Backup create/restore, manual known-node changes, player-observation metadata updates, automatic audit retention, and internal service calls outside commands are not audited.
- Undo must fail on conflicts instead of overwriting newer changes.

Detailed storage/config reference lives in [docs/STORAGE.md](docs/STORAGE.md).

## Runtime Bridges

- Paper applies effective permissions to online players with plugin-owned `PermissionAttachment`s.
- Paper attempts Paper's experimental `PermissionManager` override for registry tracking; if rejected, it falls back to registry snapshots.
- Paper expands ClutchPerms wildcard assignments onto exact known permission nodes from built-ins, manual database nodes, and Paper's permission registry.
- Paper replaces unqualified `/op` and `/deop` with ClutchPerms shortcuts when `paper.replaceOpCommands` is enabled. They must not mutate Bukkit server-op state or `ops.json`.
- Fabric exposes effective permissions through fabric-permissions-api as `TriState.TRUE`, `TriState.FALSE`, or `TriState.DEFAULT`.
- Forge and NeoForge expose effective permissions through native Boolean permission handlers registered as `clutchperms:direct`.
- Fabric, Forge, and NeoForge resend Brigadier command trees to affected online players on subject-scoped mutations and to all online players after broad permission changes or reload.
- Chat formatting is active by default and intentionally replaces the full chat line while `chat.enabled` is true.

## Commands

Command behavior belongs in `common.command` unless it depends on platform APIs. Platform command classes should be thin adapters around the shared Brigadier tree.

- `ClutchPermsCommands.ROOT_LITERALS` is the shared source of truth for `/clutchperms`, `/cperms`, and `/perms`.
- Subcommand branch shape belongs in `common.command.subcommand` classes where possible.
- Keep command feedback text centralized in `CommandLang`.
- Interactive command output belongs in `common.command`; platform adapters only render native components.
- Shared Brigadier nodes should use permission predicates so player-visible command trees and completions only expose allowed branches.
- Console and remote console can run commands for bootstrap. Players need the effective exact command permission.
- `clutchperms.admin.*` is the full admin grant. `clutchperms.admin` alone is only the namespace root and does not authorize commands.
- Destructive commands use repeat-command confirmation within 30 seconds. First confirmation runs do not audit; confirmed mutation runs audit after success.

Full command reference lives in [docs/COMMANDS.md](docs/COMMANDS.md).

## Build And Verification

- Java toolchain: 25
- Gradle wrapper: 9.4.1
- Root group: `me.clutchy.clutchperms`
- Root version: `0.1.0-SNAPSHOT`
- Main version pins live in `gradle.properties`.
- Paper tests use MockBukkit with an older Paper API line than production Paper compile. This mismatch is intentional.

Full verification:

```bash
./gradlew clean build
```

Common targeted checks:

```bash
./gradlew spotlessCheck
./gradlew :common:test
./gradlew :paper:test
./gradlew :fabric:build
./gradlew :neoforge:build
./gradlew :forge:build
```

Minimum expectations:

- Shared API, storage, resolver, or command changes: `./gradlew :common:test :paper:test`
- Paper changes: `./gradlew :paper:test`
- Fabric changes: `./gradlew :fabric:build`
- NeoForge changes: `./gradlew :neoforge:build`
- Forge changes: `./gradlew :forge:build`
- Build logic or dependency changes: `./gradlew clean build`
- Formatting-sensitive changes: `./gradlew spotlessCheck`

For cross-platform behavior changes, prefer:

```bash
./gradlew :common:test :paper:test :fabric:build :neoforge:build :forge:build spotlessCheck
```

## Coding Guidelines

- Keep shared logic in `common` whenever it is not inherently platform-specific.
- Do not introduce platform or Minecraft types into `common`.
- Prefer constructor-driven shared services and thin platform lifecycle adapters.
- Keep changes additive and small unless the user explicitly asks for a broader refactor.
- Preserve public APIs unless the requested behavior clearly requires changing them.
- Update Javadocs on public types and methods when behavior changes.
- Add comments only for non-obvious lifecycle, packaging, normalization, or test setup behavior.
- Use `./gradlew spotlessApply` after editing Java, Gradle Kotlin DSL, Markdown, JSON, YAML, properties, or supported config files.

## Packaging Rules

- Paper must continue to ship `common` classes inside the final plugin jar.
- Paper must use the server/Paper-provided SQLite JDBC driver and must not package SQLite JDBC.
- Paper should load HikariCP through `plugin.yml` `libraries`.
- Paper commands are registered through Paper lifecycle Brigadier registration, not a `plugin.yml` command block.
- `plugin.yml` should keep permission metadata accurate.
- Fabric must continue to include `common`, fabric-permissions-api, HikariCP, and SQLite JDBC as bundled jars.
- NeoForge must continue to package `common`, HikariCP, and SQLite JDBC through loader-appropriate jar-in-jar/shadow packaging.
- Forge must continue to embed `common`, HikariCP, and SQLite JDBC classes directly.
- Preserve SQLite JDBC's canonical `org.sqlite.*` packages and service metadata; do not relocate SQLite JDBC.
- Distributable runtime jars should continue to be copied into the root `build/` directory unless there is a deliberate packaging change.

## Documentation Maintenance

- Keep `README.md` focused on users and contributors.
- Keep this file focused on agent-facing implementation constraints and project guardrails.
- Keep full command syntax and permission docs in [docs/COMMANDS.md](docs/COMMANDS.md).
- Keep config, storage, backup, validation, and audit-retention details in [docs/STORAGE.md](docs/STORAGE.md).
- If command behavior changes, update shared command tests and `docs/COMMANDS.md`.
- If persisted schemas or config behavior change, update tests and `docs/STORAGE.md`.
- If platform metadata changes, update the matching metadata file and mention user-visible behavior in `README.md` when relevant.

## Commit Rules

- Use Conventional Commits: `type(scope): summary`.
- Keep the summary lowercase and omit a trailing period.
- Prefer scopes when useful: `common`, `paper`, `fabric`, `neoforge`, `forge`, `docs`, `build`, `tooling`.
- Use a body only when it adds concrete context.
- Do not amend commits unless explicitly requested.
