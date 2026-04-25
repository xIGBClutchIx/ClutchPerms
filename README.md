# ClutchPerms

ClutchPerms is an early cross-platform Minecraft permissions project for Paper, Fabric, NeoForge, and Forge. It provides shared `/clutchperms` commands, JSON storage, users, groups, terminal wildcard permissions, backup/restore support, and runtime permission bridges for each platform.

This is a usable prototype, not a mature permissions suite. The model is intentionally small: direct user permissions, inherited groups, an implicit `default` group, terminal wildcards, and shared effective permission resolution.

For first install and admin bootstrap, see [SETUP.md](SETUP.md).

## Features

- Shared command tree on Paper, Fabric, NeoForge, and Forge
- Command aliases: `/clutchperms`, `/cperms`, and `/perms`
- JSON-backed storage for permissions, groups, subjects, and known nodes
- Direct user permissions with `TRUE`, `FALSE`, and `UNSET`
- Named groups with recursive parent inheritance
- Implicit `default` group when a group named `default` exists
- Terminal wildcards: `*` and trailing `prefix.*`
- Offline targeting by stored last-known name or UUID
- Validation, reload, rolling backups, and one-file restore with rollback
- Paper wildcard expansion for exact known permission nodes
- Fabric, Forge, and NeoForge runtime permission integration

## Platforms

| Platform | Runtime integration | Storage location |
| --- | --- | --- |
| Paper | Applies effective permissions to online players through Bukkit `PermissionAttachment`s | plugin data folder |
| Fabric | Serves effective permissions through fabric-permissions-api | Fabric config dir, `clutchperms/` |
| NeoForge | Registers native handler `clutchperms:direct` | NeoForge config dir, `clutchperms/` |
| Forge | Registers native handler `clutchperms:direct` | Forge config dir, `clutchperms/` |

Paper is a Paper target. Spigot compatibility is not maintained.

## Commands

Use `/clutchperms`, `/cperms`, or `/perms`. The table uses `/clutchperms`, but every command also works through either alias.

Console and remote console can run commands for bootstrap. Players need the exact effective command permission for the command they run.

Useful grants:

| Grant | Covers |
| --- | --- |
| `clutchperms.admin.*` | Every ClutchPerms admin command |
| `clutchperms.admin.user.*` | Direct user permissions and user group membership |
| `clutchperms.admin.group.*` | Group definitions, group permissions, and group parents |
| `clutchperms.admin.backup.*` | Backup list and restore |
| `clutchperms.admin.users.*` | Stored user list and search |
| `clutchperms.admin.nodes.*` | Known permission node registry |

`clutchperms.admin` is only the namespace root. It does not grant command access by itself.

| Command | Permission | Description |
| --- | --- | --- |
| `/clutchperms` | `clutchperms.admin.help` | Shows the command list. |
| `/clutchperms status` | `clutchperms.admin.status` | Shows storage paths, counts, resolver cache counts, and runtime bridge status. |
| `/clutchperms reload` | `clutchperms.admin.reload` | Reloads all JSON files and refreshes runtime permissions. |
| `/clutchperms validate` | `clutchperms.admin.validate` | Parses all JSON files without applying them. |
| `/clutchperms backup list [kind]` | `clutchperms.admin.backup.list` | Lists backups for all files or one kind: `permissions`, `subjects`, `groups`, or `nodes`. |
| `/clutchperms backup restore <kind> <backup-file>` | `clutchperms.admin.backup.restore` | Restores one backup file, validates all storage, and reloads if valid. |
| `/clutchperms user <target> list` | `clutchperms.admin.user.list` | Lists direct permissions for a user. |
| `/clutchperms user <target> get <node>` | `clutchperms.admin.user.get` | Shows one direct user permission. |
| `/clutchperms user <target> set <node> <true\|false>` | `clutchperms.admin.user.set` | Sets one direct user permission. |
| `/clutchperms user <target> clear <node>` | `clutchperms.admin.user.clear` | Removes one direct user permission. |
| `/clutchperms user <target> check <node>` | `clutchperms.admin.user.check` | Shows the effective permission result. |
| `/clutchperms user <target> explain <node>` | `clutchperms.admin.user.explain` | Explains matching assignments and the winner. |
| `/clutchperms user <target> groups` | `clutchperms.admin.user.groups` | Lists explicit group memberships. |
| `/clutchperms user <target> group add <group>` | `clutchperms.admin.user.group.add` | Adds an explicit group membership. |
| `/clutchperms user <target> group remove <group>` | `clutchperms.admin.user.group.remove` | Removes an explicit group membership. |
| `/clutchperms group list` | `clutchperms.admin.group.list` | Lists groups. |
| `/clutchperms group <group> create` | `clutchperms.admin.group.create` | Creates a group. |
| `/clutchperms group <group> delete` | `clutchperms.admin.group.delete` | Deletes a group and related links. |
| `/clutchperms group <group> list` | `clutchperms.admin.group.view` | Lists group permissions, parents, and members. |
| `/clutchperms group <group> get <node>` | `clutchperms.admin.group.get` | Shows one direct group permission. |
| `/clutchperms group <group> set <node> <true\|false>` | `clutchperms.admin.group.set` | Sets one direct group permission. |
| `/clutchperms group <group> clear <node>` | `clutchperms.admin.group.clear` | Removes one direct group permission. |
| `/clutchperms group <group> parents` | `clutchperms.admin.group.parents` | Lists parent groups. |
| `/clutchperms group <group> parent add <parent>` | `clutchperms.admin.group.parent.add` | Adds an inheritance parent. |
| `/clutchperms group <group> parent remove <parent>` | `clutchperms.admin.group.parent.remove` | Removes an inheritance parent. |
| `/clutchperms users list` | `clutchperms.admin.users.list` | Lists stored subject metadata. |
| `/clutchperms users search <name>` | `clutchperms.admin.users.search` | Searches stored last-known names. |
| `/clutchperms nodes list` | `clutchperms.admin.nodes.list` | Lists known permission nodes. |
| `/clutchperms nodes search <query>` | `clutchperms.admin.nodes.search` | Searches known nodes and descriptions. |
| `/clutchperms nodes add <node> [description]` | `clutchperms.admin.nodes.add` | Adds or updates a manually known exact node. |
| `/clutchperms nodes remove <node>` | `clutchperms.admin.nodes.remove` | Removes a manually known node. |

Notes:

- `<target>` resolves exact online name, then exact stored last-known name, then UUID.
- Ambiguous stored names fail with matching UUIDs instead of choosing one.
- Users cannot be explicitly added to or removed from the implicit `default` group.
- Permission assignments may use exact nodes, `*`, or terminal wildcards like `example.*`.
- Mid-node wildcards such as `example.*.edit` are rejected.
- Known node registry entries must be exact nodes. Wildcards are valid assignments, not known-node entries.

## Storage

ClutchPerms writes four versioned JSON files:

| File | Purpose |
| --- | --- |
| `permissions.json` | Direct user permission assignments |
| `groups.json` | Group definitions, permissions, parent links, and memberships |
| `subjects.json` | Last-known subject names and last-seen timestamps |
| `nodes.json` | Manually registered exact known permission nodes |

Missing files load as empty state and are materialized after successful startup or reload.

Validation is strict. Malformed JSON, unsupported versions, invalid UUIDs, blank names or nodes, invalid wildcard placement, unknown permission values, unknown groups, explicit `default` memberships, unknown parent groups, and parent cycles fail startup, validate, or reload.

Backups are created before replacing an existing live JSON file. The first save of a missing file does not create a backup.

If a JSON-backed mutation cannot save, ClutchPerms leaves both the live file and the in-memory runtime state unchanged.

```text
backups/
  permissions/permissions-YYYYMMDD-HHMMSSSSS.json
  subjects/subjects-YYYYMMDD-HHMMSSSSS.json
  groups/groups-YYYYMMDD-HHMMSSSSS.json
  nodes/nodes-YYYYMMDD-HHMMSSSSS.json
```

ClutchPerms keeps the newest 10 backups per file kind.

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

Without this setting, `/clutchperms` commands and JSON storage still work, but platform permission checks continue using the default handler.

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
```

Version pins live in `gradle.properties`. Platform metadata lives in each module's `src/main/resources` directory.

## Known Limitations

- No contexts
- No group priorities
- No LuckPerms bridge or migration tooling
- No scheduled backups or multi-file restore command
- No cross-server or cross-platform synchronization
- Command targets are exact online names, exact stored last-known names, or UUIDs only
- Fabric enforcement only affects mods that query fabric-permissions-api
- Forge and NeoForge enforcement only affects registered Boolean permission nodes and requires `clutchperms:direct`

## License

No standalone license file has been added yet. Forge and NeoForge metadata currently declare `All Rights Reserved`.
