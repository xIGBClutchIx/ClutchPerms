# AGENTS.md

## Working Defaults

- Make reasonable assumptions and proceed unless the task is genuinely blocked.
- Keep changes scoped to the user request. Do not bundle unrelated refactors into feature work.
- Never revert unrelated local changes. Treat unexpected edits as user work unless proven otherwise.
- Use the Gradle wrapper: `./gradlew`.
- Run relevant checks before finishing and report exactly what ran.
- `.codex` is a local workspace file and is ignored. Do not stage it unless explicitly requested.
- NeoForge tasks may generate `neoforge/logs/`; remove that directory before finishing if it appears.

## Project Purpose

ClutchPerms is an early cross-platform Minecraft permissions prototype for Paper, Fabric, NeoForge, and Forge. It is not a mature permissions suite yet, but it does have a real core loop:

- shared permission, group, subject metadata, storage, and command code in `common`
- JSON-backed persisted state
- shared Brigadier `/clutchperms` commands
- Paper runtime attachments and known-node wildcard expansion
- Fabric fabric-permissions-api integration
- Forge and NeoForge native permission handlers

Keep the project moving toward a useful permissions system without adding mature-suite complexity too early.

## Repository Layout

- `common`
  - pure Java shared code
  - no Bukkit, Paper, Fabric, NeoForge, Forge, or Minecraft dependencies
  - owns the shared permission model, storage model, effective resolution, and command behavior
- `paper`
  - Paper plugin adapter
  - Paper-only target; Spigot compatibility is not maintained
  - embeds compiled `common` classes in the plugin jar
- `fabric`
  - Fabric mod adapter built with Loom
  - packages `common` and fabric-permissions-api as nested included jars
- `neoforge`
  - NeoForge mod adapter built with ModDevGradle
  - packages `common` as a nested jar through jar-in-jar metadata
- `forge`
  - Forge mod adapter built with ForgeGradle
  - embeds compiled `common` classes in the mod jar

Shared package ownership:

- `common.permission` - direct permissions, node normalization, wildcard matching, effective resolution, permission observers, and permission service factories
- `common.group` - group definitions, group permissions, memberships, group storage, and group observers
- `common.node` - known permission node registry, manual node storage, registry composition, and node observers
- `common.subject` - last-known subject metadata
- `common.storage` - storage exceptions
- `common.command` - shared Brigadier command tree, command messages, and platform command environment contract

## Current Functional Model

- Direct permissions are stored by subject `UUID` and normalized permission node.
- Permission nodes normalize with `trim().toLowerCase(Locale.ROOT)`.
- Permission nodes may be exact nodes, `*`, or terminal wildcard nodes ending in `.*`.
- Wildcard nodes containing `*` anywhere else are invalid and must fail mutation, startup load, or reload.
- Stored values are `TRUE`, `FALSE`, or absent. Absence is `UNSET`.
- `PermissionService#hasPermission(...)` is true only for `TRUE`.
- Groups are named, normalized, and store explicit permission assignments.
- Groups can inherit multiple parent groups recursively.
- User group membership is direct only.
- A group named `default` applies implicitly to every subject when it exists.
- Users cannot be explicitly added to or removed from `default`.
- Effective resolution order is direct user assignment, explicit user group hierarchy, then implicit `default` hierarchy.
- Closer child group permissions beat parent permissions.
- Within the same inheritance depth, `FALSE` wins over `TRUE`.
- Wildcard resolution happens inside each source tier/depth: exact node, closest `prefix.*`, broader parent wildcards, then `*`.
- `prefix.*` matches descendant nodes such as `prefix.child` and `prefix.child.deep`, but not `prefix`.
- Direct wildcard assignments still beat group/default exact assignments because source tier precedence comes first.
- Known permission nodes are advisory exact-node descriptors used for discovery, suggestions, diagnostics, and Paper wildcard expansion.
- `nodes.json` stores only manually registered exact nodes. Platform-discovered nodes are runtime-only and must not be written back to `nodes.json`.
- Unknown permission nodes remain assignable; do not make assignment mutation depend on the known-node registry.
- Subject metadata stores UUID, last-known name, and last-seen timestamp.
- Command targets resolve exact online name first, exact stored last-known name second, and UUID third.
- Ambiguous stored last-known names must fail clearly instead of mutating an arbitrary UUID.

Do not add contexts, priorities, imports, migrations, or LuckPerms bridges unless the user asks for that slice.

## Storage And Reload

Current persisted files:

- `permissions.json` for direct user permission assignments
- `groups.json` for group definitions, group permissions, parent links, and memberships
- `subjects.json` for subject metadata
- `nodes.json` for manually registered exact known permission nodes

Locations:

- Paper: plugin data folder
- Fabric: Fabric config dir, `clutchperms/`
- NeoForge: NeoForge config dir, `clutchperms/`
- Forge: Forge config dir, `clutchperms/`

Storage expectations:

- Treat missing files as empty state.
- Save mutations immediately.
- Create parent directories as needed.
- Use deterministic output.
- Write through temporary files and replace the target file.
- Fail startup, validate, or reload on malformed JSON, unsupported versions, invalid UUIDs, blank names/nodes, invalid wildcard placement, wildcard known-node registry entries, unknown permission values, unknown membership groups, explicit `default` memberships, unknown parent groups, and parent cycles.
- `/clutchperms validate` should parse all persisted files without replacing active services, refreshing runtime bridges, or mutating storage.
- Reload should be atomic from the command perspective: if any file fails, keep active runtime state unchanged.

## Runtime Bridges

- Paper applies effective permissions to online players with plugin-owned `PermissionAttachment`s.
- Paper attempts Paper's experimental `PermissionManager` override for registry tracking. If Paper rejects it, the plugin falls back to registry snapshots and continues enabling.
- Paper expands ClutchPerms wildcard assignments onto exact known permission nodes from built-ins, manual `nodes.json`, and Paper's permission registry.
- Paper attachments include stored wildcard nodes, but Bukkit/Paper does not expand arbitrary unregistered wildcard checks for ClutchPerms; avoid claiming true arbitrary Paper wildcard interception without `Permissible` injection or another deeper Paper-specific bridge.
- Paper bridge refreshes on join, service mutation, reload, and disable/quit cleanup.
- Fabric exposes effective permissions through fabric-permissions-api as `TriState.TRUE`, `TriState.FALSE`, or `TriState.DEFAULT`.
- Forge and NeoForge expose effective permissions through native Boolean permission handlers registered as `clutchperms:direct`.
- Forge and NeoForge only affect mods that use the platform permission APIs and only when server config selects `clutchperms:direct`.
- Keep bridge code thin. Shared behavior belongs in `common`; platform code should adapt lifecycle/source/runtime APIs.

## Commands

Command behavior belongs in `common.command` unless it depends on platform APIs. Platform command classes should be thin adapters around the shared Brigadier tree.

Current command surface:

- `/clutchperms`
- `/clutchperms status`
- `/clutchperms reload`
- `/clutchperms validate`
- `/clutchperms user <target> list|get|set|clear|check|explain`
- `/clutchperms user <target> groups`
- `/clutchperms user <target> group add|remove <group>`
- `/clutchperms group list`
- `/clutchperms group <group> create|delete|list|get|set|clear`
- `/clutchperms group <group> parents`
- `/clutchperms group <group> parent add|remove <parent>`
- `/clutchperms users list`
- `/clutchperms users search <name>`
- `/clutchperms nodes list|search <query>`
- `/clutchperms nodes add <node> [description]`
- `/clutchperms nodes remove <node>`

Authorization:

- Console and remote console can run commands for bootstrap.
- Players need effective `clutchperms.admin`.
- Other source types should be denied where the platform can distinguish them.
- `check` is short effective-value feedback. `explain` should show matching assignments in resolver order and identify the winning assignment.
- Node registry commands mutate only the manual registry. Built-in and platform-discovered known nodes are visible but not removable.

## Build And Versions

- Java toolchain: 25
- Gradle wrapper: 9.4.1
- Root group: `me.clutchy.clutchperms`
- Root version: `0.1.0-SNAPSHOT`
- Main version pins live in `gradle.properties`.
- Do not downgrade the Gradle wrapper unless the Fabric/Loom setup changes too.
- Paper tests use MockBukkit with an older Paper API line than production Paper compile. This mismatch is intentional.
- Version bumps can ripple through metadata, packaging, MockBukkit compatibility, or toolchain requirements. Read the relevant module build file before changing dependencies.

Key metadata files:

- `paper/src/main/resources/plugin.yml`
- `fabric/src/main/resources/fabric.mod.json`
- `neoforge/src/main/resources/META-INF/neoforge.mods.toml`
- `forge/src/main/resources/META-INF/mods.toml`

## Verification

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
- Keep command feedback text centralized in `common.command.CommandLang`.
- Keep JSON schemas strict and deterministic.
- Do not silently start with empty state after a bad persisted file.

## Packaging Rules

- Paper must continue to ship `common` classes inside the final plugin jar.
- Paper commands are registered through Paper lifecycle Brigadier registration, not a `plugin.yml` command block.
- `plugin.yml` should keep permission metadata accurate.
- Fabric must continue to include `common` and fabric-permissions-api as nested jars while the bridge depends on fabric-permissions-api.
- NeoForge must continue to package `common` through jar-in-jar metadata.
- Forge must continue to embed `common` classes directly.
- Distributable runtime jars should continue to be copied into the root `build/` directory unless there is a deliberate packaging change.

## Formatting

- Formatting is enforced by Spotless.
- Use `./gradlew spotlessApply` after editing Java, Gradle Kotlin DSL, Markdown, JSON, YAML, properties, or supported config files.
- Java formatting uses the Eclipse JDT profile in `eclipse-java-formatter.xml`.
- Java and Gradle Kotlin DSL line length target is 180.
- Keep `.editorconfig` aligned with Spotless if formatting rules change.

## Documentation Maintenance

- Keep `README.md` focused on users and contributors.
- Keep this file focused on agent-facing implementation constraints and project guardrails.
- Update both `README.md` and `AGENTS.md` when behavior, command syntax, storage paths, platform support, packaging, or verification expectations change.
- If command behavior changes, update shared command tests and README command docs.
- If persisted schemas change, update tests, README examples, and storage validation notes.
- If platform metadata changes, update the matching metadata file and mention user-visible behavior in README.

## Commit Rules

- Use Conventional Commits: `type(scope): summary`.
- Keep the summary lowercase and omit a trailing period.
- Prefer scopes when useful: `common`, `paper`, `fabric`, `neoforge`, `forge`, `docs`, `build`, `tooling`.
- Use a body only when it adds concrete context.
- Do not amend commits unless explicitly requested.
