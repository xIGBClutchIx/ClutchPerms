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

ClutchPerms is an early cross-platform Minecraft permissions prototype for Paper, Fabric, NeoForge, and Forge. It is not a mature permissions suite yet, but it does have a real core loop:

- shared permission, group, subject metadata, storage, and command code in `common`
- JSON-backed persisted state
- shared Brigadier `/clutchperms` commands
- shared user/group display values for prefixes, suffixes, and chat rendering
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

- `common.permission` - direct permissions, node normalization, wildcard matching, cached effective resolution, permission observers, and permission service factories
- `common.group` - group definitions, group permissions, memberships, group storage, and group observers
- `common.display` - ampersand-formatted prefix/suffix parsing and effective display resolution
- `common.node` - known permission node registry, manual node storage, registry composition, and node observers
- `common.subject` - last-known subject metadata
- `common.config` - runtime config parsing, defaults, validation, and materialization
- `common.storage` - storage exceptions, atomic file writes, backup listing, and restore rollback helpers
- `common.runtime` - platform-neutral storage paths, active service snapshots, reload/validate/backup wiring, resolver cache invalidation, and runtime refresh hooks
- `common.command` - shared Brigadier root wiring, command behavior, command messages, and platform command environment contract
- `common.command.subcommand` - shared Brigadier branch builders for `/backup`, `/user`, `/group`, `/users`, and `/nodes`

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
- The `default` group always exists, applies implicitly to every subject, and cannot be deleted.
- Users cannot be explicitly added to or removed from `default`.
- Effective resolution order is direct user assignment, explicit user group hierarchy, then implicit `default` hierarchy.
- Closer child group permissions beat parent permissions.
- Within the same inheritance depth, `FALSE` wins over `TRUE`.
- Wildcard resolution happens inside each source tier/depth: exact node, closest `prefix.*`, broader parent wildcards, then `*`.
- `prefix.*` matches descendant nodes such as `prefix.child` and `prefix.child.deep`, but not `prefix`.
- Direct wildcard assignments still beat group/default exact assignments because source tier precedence comes first.
- Effective permission resolution is cached in memory by subject/node and by subject effective-permission snapshot.
- Direct permission and membership mutations invalidate one subject; group definition, group permission, and parent mutations clear the full resolver cache.
- Reload and restore replace the active resolver, so cache state is runtime-only and starts empty after reload/startup.
- Known permission nodes are advisory exact-node descriptors used for discovery, suggestions, diagnostics, and Paper wildcard expansion.
- `nodes.json` stores only manually registered exact nodes. Platform-discovered nodes are runtime-only and must not be written back to `nodes.json`.
- Unknown permission nodes remain assignable; do not make assignment mutation depend on the known-node registry.
- Subject metadata stores UUID, last-known name, and last-seen timestamp.
- Subject metadata may also store direct user prefix/suffix display values.
- Groups may store prefix/suffix display values.
- Display text is ampersand-formatted only: support `&0-9`, `&a-f`, `&k-o`, `&r`, and `&&`; reject raw section signs, invalid codes, blank values, and raw values over 128 characters.
- Effective display prefix and suffix resolve independently: direct user value, nearest explicit group hierarchy, then nearest `default` hierarchy.
- Same-depth display ties are deterministic and sort by group name.
- Command targets resolve exact online name first, exact stored last-known name second, and UUID third.
- Ambiguous stored last-known names must fail clearly instead of mutating an arbitrary UUID.

Do not add contexts, priorities, imports, migrations, or LuckPerms bridges unless the user asks for that slice.

## Storage And Reload

Current persisted files:

- `config.json` for runtime settings such as backup retention, command page sizes, and chat formatting
- `permissions.json` for direct user permission assignments
- `groups.json` for group definitions, group permissions, prefix/suffix display values, parent links, and memberships
- `subjects.json` for subject metadata and direct user prefix/suffix display values
- `nodes.json` for manually registered exact known permission nodes

Locations:

- Paper: plugin data folder
- Fabric: Fabric config dir, `clutchperms/`
- NeoForge: NeoForge config dir, `clutchperms/`
- Forge: Forge config dir, `clutchperms/`

Storage expectations:

- Treat missing storage files as empty state, except group storage starts with the built-in `default` group. Treat missing `config.json` as default config.
- After successful startup or reload, materialize missing storage/config files with versioned JSON so fresh installs have visible files, including `default` in `groups.json`.
- Save mutations immediately.
- Create parent directories as needed.
- Use deterministic output.
- Write through temporary files and replace the target file.
- Before replacing an existing live JSON file, create a rolling backup through `common.storage.StorageBackupService`.
- Do not replace the live file if backup creation fails.
- JSON-backed mutations must commit in-memory runtime state only after the replacement file is successfully written.
- If a mutation save fails, keep the previous in-memory state, resolver cache notifications, runtime bridge notifications, and live JSON file unchanged.
- The first save of a missing live file must not create a backup.
- Keep backup retention controlled by `config.json` `backups.retentionLimit`, defaulting to 10 newest files per storage kind.
- In-game config management covers `backups.retentionLimit`, `commands.helpPageSize`, `commands.resultPageSize`, and `chat.enabled`.
- Backup layout is `backups/<kind>/<kind>-YYYYMMDD-HHMMSSSSS.json`, where kind is `permissions`, `subjects`, `groups`, or `nodes`.
- Backup roots are Paper plugin data folder `backups/` and Fabric/NeoForge/Forge config dir `clutchperms/backups/`.
- Fail startup, validate, or reload on malformed JSON, unsupported versions, unknown config keys, invalid config values, invalid UUIDs, blank names/nodes, duplicate normalized permission keys, invalid wildcard placement, wildcard known-node registry entries, unknown permission values, unknown membership groups, explicit `default` memberships, unknown parent groups, and parent cycles.
- Fail startup, validate, or reload on invalid stored display text, including raw section signs, invalid ampersand codes, blank display values, and over-length display values.
- `/clutchperms validate` should parse config and all persisted files without replacing active services/config, refreshing runtime bridges, or mutating storage.
- Reload should be atomic from the command perspective: if any file fails, keep active runtime state unchanged.
- Successful reload should replace active services, active config, and the active resolver cache; failed reload should leave the old config, resolver, and resolver cache in place.
- Shared storage lifecycle wiring belongs in `common.runtime.ClutchPermsRuntime`; platform modules should provide storage roots, platform known-node suppliers, runtime refresh hooks, service registration, logging, and lifecycle events.
- `/clutchperms backup restore` validates the selected backup file before replacing live storage, then restores one file, reloads config plus all four persisted storage files, and refreshes runtime bridges. If pre-restore validation fails, disk and active runtime state must remain unchanged. If reload fails after replacement, it must roll disk back to the previous live file and keep active services/runtime state unchanged.
- `config.json` is not included in backup list or restore commands in this version.
- If restore rollback fails, command feedback should report that rollback failure explicitly.

## Runtime Bridges

- Paper applies effective permissions to online players with plugin-owned `PermissionAttachment`s.
- Paper attempts Paper's experimental `PermissionManager` override for registry tracking. If Paper rejects it, the plugin falls back to registry snapshots and continues enabling.
- Paper expands ClutchPerms wildcard assignments onto exact known permission nodes from built-ins, manual `nodes.json`, and Paper's permission registry.
- Paper attachments include stored wildcard nodes, but Bukkit/Paper does not expand arbitrary unregistered wildcard checks for ClutchPerms; avoid claiming true arbitrary Paper wildcard interception without `Permissible` injection or another deeper Paper-specific bridge.
- Paper bridge refreshes on join, service mutation, reload, and disable/quit cleanup.
- Paper formats chat through `AsyncChatEvent` renderers as `prefix name suffix: message` using native Adventure components when `chat.enabled` is true.
- Fabric exposes effective permissions through fabric-permissions-api as `TriState.TRUE`, `TriState.FALSE`, or `TriState.DEFAULT`.
- Fabric formats server chat through a server-side mixin that broadcasts the full formatted line as a native Minecraft component when `chat.enabled` is true.
- Forge and NeoForge expose effective permissions through native Boolean permission handlers registered as `clutchperms:direct`.
- Forge and NeoForge format chat through `ServerChatEvent` by replacing the full chat line with native Minecraft components when `chat.enabled` is true.
- Forge and NeoForge only affect mods that use the platform permission APIs and only when server config selects `clutchperms:direct`.
- Prefix/suffix chat formatting is active by default and intentionally takes priority over vanilla signed-chat presentation while `chat.enabled` is true; do not claim that formatted chat remains vanilla-signed on every loader/client.
- Keep bridge code thin. Shared behavior belongs in `common`; platform code should adapt lifecycle/source/runtime APIs.

## Commands

Command behavior belongs in `common.command` unless it depends on platform APIs. Platform command classes should be thin adapters around the shared Brigadier tree.

Command system layout:

- `ClutchPermsCommands` owns the root `/clutchperms` builder, authorization wrapper, shared command actions, target parsing, and common feedback helpers.
- `ClutchPermsCommands.ROOT_LITERALS` is the shared source of truth for registered root command literals. Current roots are `/clutchperms`, `/cperms`, and `/perms`.
- Subcommand branch shape belongs in `common.command.subcommand` classes such as `BackupSubcommand`, `UserSubcommand`, `GroupSubcommand`, `UsersSubcommand`, and `NodesSubcommand`.
- Subcommand classes should build Brigadier literals/arguments, suggestions that are local to the branch, and handler interfaces. They should not introduce platform APIs or duplicate command behavior that belongs in `ClutchPermsCommands`.
- Use `CommandArguments` constants for shared argument names so handlers and branch builders stay aligned.
- Keep command feedback text centralized in `CommandLang`; branch builders should call handlers rather than formatting user-facing messages directly.
- `CommandMessage` owns shared styling plus optional click/hover metadata. Keep `plainText()` stable for tests and string fallback output.
- Interactive command output belongs in `common.command`. Platform adapters should only render native components: Paper Adventure components, and Minecraft components for Fabric, NeoForge, and Forge.
- Do not use legacy section-sign formatting. Keep the restrained command palette: aqua headings, gray metadata/navigation, white command text, yellow placeholders/values, green success, and red errors.
- Command rows and result rows should suggest/paste commands. Page controls are the only shared command output clicks that should run commands.

Current command surface:

- `/clutchperms`
- `/cperms`
- `/perms`
- `/clutchperms help [page]`
- `/clutchperms status`
- `/clutchperms reload`
- `/clutchperms validate`
- `/clutchperms config list`
- `/clutchperms config get <key>`
- `/clutchperms config set <key> <value>`
- `/clutchperms config reset <key|all>`
- `/clutchperms backup list`
- `/clutchperms backup list page <page>`
- `/clutchperms backup list <permissions|subjects|groups|nodes> [page]`
- `/clutchperms backup restore <permissions|subjects|groups|nodes> <backup-file>`
- `/clutchperms user <target> list [page]`
- `/clutchperms user <target> get|set|clear|check|explain`
- `/clutchperms user <target> groups [page]`
- `/clutchperms user <target> group add|remove <group>`
- `/clutchperms user <target> prefix get|set|clear`
- `/clutchperms user <target> suffix get|set|clear`
- `/clutchperms group list [page]`
- `/clutchperms group <group> create|delete|get|set|clear`
- `/clutchperms group <group> list [page]`
- `/clutchperms group <group> prefix get|set|clear`
- `/clutchperms group <group> suffix get|set|clear`
- `/clutchperms group <group> parents [page]`
- `/clutchperms group <group> parent add|remove <parent>`
- `/clutchperms users list [page]`
- `/clutchperms users search <name> [page]`
- `/clutchperms nodes list [page]`
- `/clutchperms nodes search <query> [page]`
- `/clutchperms nodes add <node> [description]`
- `/clutchperms nodes remove <node>`

Authorization:

- Console and remote console can run commands for bootstrap.
- Players need the effective exact command permission for the command they run.
- Use `clutchperms.admin.*` as the full ClutchPerms admin grant.
- Category wildcards such as `clutchperms.admin.user.*`, `clutchperms.admin.group.*`, `clutchperms.admin.backup.*`, `clutchperms.admin.nodes.*`, and `clutchperms.admin.users.*` should work through the shared resolver.
- Config command permissions are `clutchperms.admin.config.view`, `clutchperms.admin.config.set`, and `clutchperms.admin.config.reset`; `clutchperms.admin.config.*` should work through wildcard resolution.
- User display command permissions are `clutchperms.admin.user.display.view`, `clutchperms.admin.user.display.set`, and `clutchperms.admin.user.display.clear`; `clutchperms.admin.user.*` should cover them.
- Group display command permissions are `clutchperms.admin.group.display.view`, `clutchperms.admin.group.display.set`, and `clutchperms.admin.group.display.clear`; `clutchperms.admin.group.*` should cover them.
- `clutchperms.admin` is only the namespace root and does not authorize commands.
- Other source types should be denied where the platform can distinguish them.
- `status` should include storage paths, subject/group/node counts, resolver cache counts, and platform bridge status.
- Config command changes should save `config.json`, reload runtime immediately, refresh runtime bridges, and roll `config.json` back if reload fails. Same-value config changes should not write or reload.
- `check` is short effective-value feedback. `explain` should show matching assignments in resolver order and identify the winning assignment.
- Bad user, group, parent group, backup kind/file, and manual known-node targets should return styled shared feedback with deterministic closest matches: case-insensitive prefix matches first, then substring matches, then small edit-distance matches, capped at 5 suggestions.
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
