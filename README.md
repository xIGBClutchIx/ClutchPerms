# ClutchPerms

ClutchPerms is an early cross-platform Minecraft permissions project for Paper, Fabric, NeoForge, and Forge. It provides shared `/clutchperms` commands, SQLite storage, users, groups, ordered tracks, terminal wildcard permissions, prefixes/suffixes, backups, audit history, and runtime permission bridges for each platform.

This is a usable prototype, not a mature permissions suite. The model is intentionally small: direct user permissions, inherited groups, built-in `default` and `op` groups, terminal wildcards, and shared effective permission resolution.

For first install and admin bootstrap, see [SETUP.md](SETUP.md).

## Features

- Shared `/clutchperms`, `/cperms`, and `/perms` command tree on every supported loader
- Direct user permissions with `TRUE`, `FALSE`, and `UNSET`
- Named groups with recursive parent inheritance
- Ordered tracks for admin promote and demote flows
- Built-in implicit `default` group and protected explicit-membership `op` group
- Exact permission nodes, `*`, and terminal wildcards like `example.*`
- Offline targeting by exact resolvable name, stored last-known name, or UUID
- User and group prefixes/suffixes with ampersand formatting
- Cross-platform chat display as `prefix name suffix: message`
- SQLite validation, reload, database snapshots, restore with rollback, audit history, and undo
- Live command tree refresh after permission changes so player completions update without relogging
- Paper wildcard expansion for exact known permission nodes
- Fabric, Forge, and NeoForge runtime permission integration

## Platforms

| Platform | Runtime integration | Storage location |
| --- | --- | --- |
| Paper | Applies effective permissions through Bukkit `PermissionAttachment`s and formats chat with Adventure components | plugin data folder |
| Fabric | Serves effective permissions through fabric-permissions-api and formats server chat with a server-side mixin | Fabric config dir, `clutchperms/` |
| NeoForge | Registers native handler `clutchperms:direct` and formats `ServerChatEvent` output | NeoForge config dir, `clutchperms/` |
| Forge | Registers native handler `clutchperms:direct` and formats `ServerChatEvent` output | Forge config dir, `clutchperms/` |

Paper is a Paper target. Spigot compatibility is not maintained.

## Command Basics

Use `/clutchperms`, `/cperms`, or `/perms`. Console and remote console can run commands for bootstrap. Players need the effective exact command permission for the command they run, and player command trees/completions only expose branches backed by permissions they currently have.

Useful grants:

| Grant | Covers |
| --- | --- |
| `clutchperms.admin.*` | Every ClutchPerms admin command |
| `clutchperms.admin.user.*` | Direct user permissions, user group membership, and user display values |
| `clutchperms.admin.group.*` | Group definitions, group permissions, group members, group parents, and group display values |
| `clutchperms.admin.user.track.*` | User track listing plus promote and demote actions |
| `clutchperms.admin.track.*` | Track definitions and ordered group management |
| `clutchperms.admin.config.*` | Runtime config view, set, and reset |
| `clutchperms.admin.backup.*` | Backup create, list, schedule status, run-now, and restore |
| `clutchperms.admin.history` | Audit history listing |
| `clutchperms.admin.history.*` | Audit history pruning |
| `clutchperms.admin.undo` | Undoing undoable audit history entries |

`clutchperms.admin` is only the namespace root. It does not grant command access by itself.

Destructive commands require repeat-command confirmation within 30 seconds. This covers user/group clear-all, group delete, track delete, backup restore, and audit history prune commands.

Full command reference: [docs/COMMANDS.md](docs/COMMANDS.md)

## Permission Model

The `default` group always exists, applies implicitly to every subject, and cannot be deleted or renamed. Users cannot be explicitly added to or removed from `default`.

The protected `op` group always exists, grants `* = TRUE`, has no members by default, and only affects users explicitly added to it. It cannot be deleted, renamed, edited for permissions/display, or used in parent links.

Effective resolution order is direct user assignment, explicit user group hierarchy, then implicit `default` hierarchy. Closer child group permissions beat parent permissions; within the same inheritance depth, `FALSE` wins over `TRUE`.

Tracks define ordered group ladders for admin promote and demote helpers. They do not affect effective permission or display resolution on their own. `default` may appear only as the first entry on a track, `op` cannot appear on a track, and manual user-group adds or removes remain available for exceptional cases outside a track flow.

## Paper `/op` And `/deop`

On Paper, ClutchPerms can register unqualified `/op` and `/deop` command roots. These commands add or remove explicit membership in the protected `op` group and do not change Bukkit server-op state or `ops.json`.

This replacement is enabled by default. Set `paper.replaceOpCommands` to `false` before startup to leave Paper's command labels alone.

## Storage And Config

ClutchPerms writes one config file and one SQLite database:

| File | Purpose |
| --- | --- |
| `config.json` | Runtime settings for backup retention/scheduling, audit retention, command page sizes, chat formatting, and Paper command replacement |
| `database.db` | Direct permissions, group definitions, track definitions, memberships, subject metadata, display values, manually registered known nodes, and command audit history |

Missing database storage loads as empty state with the built-in `default` and `op` groups. Missing `config.json` loads defaults. Missing files are materialized after successful startup or reload.

Backups are whole-database snapshots created manually with `/clutchperms backup create` or automatically when `backups.schedule.enabled` is true. Scheduled backups are disabled by default.

Audit history retention is enabled by default. ClutchPerms keeps audit rows for 90 days, with no count cap unless `audit.retention.maxEntries` is set above `0`.

Storage and config reference: [docs/STORAGE.md](docs/STORAGE.md)

## Forge And NeoForge

Forge and NeoForge only use one active permission handler. Select ClutchPerms in the server config before other mods resolve permissions through it.

NeoForge:

```toml
permissionHandler = "clutchperms:direct"
```

Forge:

```toml
[server]
permissionHandler = "clutchperms:direct"
```

Without this setting, `/clutchperms` commands and SQLite storage still work, but platform permission checks continue using the default handler.

## Build

Use Java 25 and the Gradle wrapper.

```bash
./gradlew clean build
```

Common checks:

```bash
./gradlew spotlessCheck
./gradlew :common:test
./gradlew :paper:test
./gradlew :fabric:build
./gradlew :neoforge:build
./gradlew :forge:build
```

Runtime jars are copied to the root `build/` directory.

## Project Layout

```text
common/    Shared permissions, groups, subjects, storage, and commands
paper/     Paper plugin adapter and runtime permission bridge
fabric/    Fabric mod adapter and fabric-permissions-api bridge
neoforge/  NeoForge mod adapter and native permission handler
forge/     Forge mod adapter and native permission handler
docs/      Command, storage, and operational references
```

Version pins live in `gradle.properties`. Platform metadata lives in each module's `src/main/resources` directory.

## Known Limitations

- No contexts
- No group priorities
- No LuckPerms bridge or migration tooling
- No multi-file restore command
- No cross-server or cross-platform synchronization
- Command targets resolve exact online names first, then exact resolvable offline names, exact stored last-known names, and UUIDs
- Fabric enforcement only affects mods that query fabric-permissions-api
- Forge and NeoForge enforcement only affects registered Boolean permission nodes and requires `clutchperms:direct`

## License

No standalone license file has been added yet. Forge and NeoForge metadata currently declare `All Rights Reserved`.
