# ClutchPerms

ClutchPerms is an early cross-platform Minecraft permissions project for Paper, Fabric, NeoForge, and Forge. It currently provides a shared permission model, JSON storage, Brigadier commands, basic inherited groups, terminal wildcard permissions, subject metadata, a known permission node registry, runtime resolver caching, validation/reload/backup support, and platform runtime bridges.

This is a usable prototype, not a mature permissions suite. The project intentionally keeps the model small: direct user permissions, recursive multi-parent groups, an implicit `default` group, terminal wildcards, and effective permission resolution shared across platforms.

For first install and admin bootstrap, see [SETUP.md](SETUP.md).

## What Works

- Shared `/clutchperms` command behavior on Paper, Fabric, NeoForge, and Forge
- JSON-backed storage for direct permissions, groups, subject metadata, and manually known permission nodes
- Direct user permission `TRUE` / `FALSE` / `UNSET`
- Basic named groups with direct user membership and recursive multi-parent inheritance
- Implicit `default` group when a group named `default` exists
- Effective resolution order: direct user assignment, explicit user group hierarchy, then `default` hierarchy
- Closer child group permissions beat parent permissions; `FALSE` wins over `TRUE` at the same inheritance depth
- Terminal wildcard assignments: `*` and trailing `prefix.*`
- Validation and reload commands for manual JSON edits
- Last-known player name recording and offline name targeting
- Advisory known permission node registry for command discovery and Paper wildcard expansion
- Runtime effective-permission resolver caching with mutation/reload invalidation
- Rolling per-file backups before JSON saves, plus one-file restore commands with rollback on failed reload
- Paper runtime bridge using plugin-owned `PermissionAttachment`s and known-node wildcard expansion
- Fabric runtime bridge through fabric-permissions-api
- Forge and NeoForge runtime handlers through their native permission APIs

## Platform Status

| Platform | Runtime integration | Storage location |
| --- | --- | --- |
| Paper | Applies effective permissions to online players through Bukkit `PermissionAttachment`s; attempts Paper's experimental permission manager override for registry tracking | plugin data folder |
| Fabric | Serves effective permissions through fabric-permissions-api | Fabric config dir, `clutchperms/` |
| NeoForge | Registers native handler `clutchperms:direct` | NeoForge config dir, `clutchperms/` |
| Forge | Registers native handler `clutchperms:direct` | Forge config dir, `clutchperms/` |

Paper is a Paper target. Spigot compatibility is not maintained.

Paper note: ClutchPerms attaches stored wildcard nodes such as `example.*` and expands wildcard results onto exact known permission nodes. Known nodes come from `nodes.json`, ClutchPerms built-ins, and Paper's permission registry. Bukkit permission attachments still do not expand arbitrary unknown wildcard checks by themselves. Paper command authorization uses ClutchPerms wildcard resolution directly.

## Commands

All platforms expose the same shared command tree through `/clutchperms`, `/cperms`, and `/perms`. Console and remote console can run commands for bootstrap. Players need the effective command permission for the exact command they run.

Useful command grants:

| Grant | Covers |
| --- | --- |
| `clutchperms.admin.*` | Every ClutchPerms admin command |
| `clutchperms.admin.backup.*` | Backup list and restore commands |
| `clutchperms.admin.user.*` | Direct user permission commands and user group membership commands |
| `clutchperms.admin.group.*` | Group definition, group permission, and group parent commands |
| `clutchperms.admin.users.*` | Stored user list and search commands |
| `clutchperms.admin.nodes.*` | Known permission node registry commands |

`clutchperms.admin` is only the namespace root and does not grant command access. Non-player/non-console sources are denied where the platform can distinguish them.

| Command | Permission | Description | Notes |
| --- | --- | --- | --- |
| `/clutchperms` (`/cperms`, `/perms`) | `clutchperms.admin.help` | Shows the command list. | Incomplete command branches also return contextual `Try one:` suggestions. |
| `/clutchperms status` | `clutchperms.admin.status` | Shows storage paths, subject count, group count, known node count, resolver cache counts, and runtime bridge status. | Paper also reports permission manager override mode. |
| `/clutchperms reload` | `clutchperms.admin.reload` | Reloads all JSON storage files and refreshes runtime permissions. | If any file is invalid, active runtime state is kept unchanged. |
| `/clutchperms validate` | `clutchperms.admin.validate` | Parses all JSON storage files without applying them. | Does not replace active services or refresh runtime permissions. |
| `/clutchperms backup list` | `clutchperms.admin.backup.list` | Lists backups for all storage files, newest first. | Read-only. |
| `/clutchperms backup list <permissions\|subjects\|groups\|nodes>` | `clutchperms.admin.backup.list` | Lists backups for one storage file kind. | Use the exact kind token shown in the command. |
| `/clutchperms backup restore <permissions\|subjects\|groups\|nodes> <backup-file>` | `clutchperms.admin.backup.restore` | Restores one backup file, then validates and reloads all storage. | Rolls the disk file back and keeps active runtime state unchanged if reload fails. |
| `/clutchperms user <target> list` | `clutchperms.admin.user.list` | Lists direct permission assignments for one user. | `<target>` accepts an online name, stored last-known name, or UUID. |
| `/clutchperms user <target> get <node>` | `clutchperms.admin.user.get` | Shows one direct user permission assignment. | Reports `UNSET` when the user has no direct value for that node. |
| `/clutchperms user <target> set <node> <true\|false>` | `clutchperms.admin.user.set` | Sets a direct user permission assignment. | Saves immediately and refreshes runtime permissions for that subject. |
| `/clutchperms user <target> clear <node>` | `clutchperms.admin.user.clear` | Removes a direct user permission assignment. | Saves immediately and refreshes runtime permissions for that subject. |
| `/clutchperms user <target> check <node>` | `clutchperms.admin.user.check` | Shows the effective permission value for a user. | Includes the winning source and wildcard assignment when applicable. |
| `/clutchperms user <target> explain <node>` | `clutchperms.admin.user.explain` | Explains how the effective value was resolved. | Lists matching direct, group, and default assignments in resolver order and marks the winner. |
| `/clutchperms user <target> groups` | `clutchperms.admin.user.groups` | Lists a user's explicit group memberships. | The implicit `default` group is not listed as explicit membership. |
| `/clutchperms user <target> group add <group>` | `clutchperms.admin.user.group.add` | Adds a user to a group. | The `default` group cannot be assigned explicitly. |
| `/clutchperms user <target> group remove <group>` | `clutchperms.admin.user.group.remove` | Removes a user from a group. | The `default` group cannot be removed explicitly. |
| `/clutchperms group list` | `clutchperms.admin.group.list` | Lists groups. | Read-only. |
| `/clutchperms group <group> create` | `clutchperms.admin.group.create` | Creates a group. | Group names are normalized by the shared group service. |
| `/clutchperms group <group> delete` | `clutchperms.admin.group.delete` | Deletes a group. | Also removes related memberships and inheritance links through the group service. |
| `/clutchperms group <group> list` | `clutchperms.admin.group.view` | Lists a group's direct permission assignments. | Read-only. |
| `/clutchperms group <group> get <node>` | `clutchperms.admin.group.get` | Shows one direct group permission assignment. | Reports `UNSET` when the group has no direct value for that node. |
| `/clutchperms group <group> set <node> <true\|false>` | `clutchperms.admin.group.set` | Sets a direct group permission assignment. | Saves immediately and clears the resolver cache. |
| `/clutchperms group <group> clear <node>` | `clutchperms.admin.group.clear` | Removes a direct group permission assignment. | Saves immediately and clears the resolver cache. |
| `/clutchperms group <group> parents` | `clutchperms.admin.group.parents` | Lists parent groups for a group. | Read-only. |
| `/clutchperms group <group> parent add <parent>` | `clutchperms.admin.group.parent.add` | Adds an inheritance parent. | Parent cycles are rejected. |
| `/clutchperms group <group> parent remove <parent>` | `clutchperms.admin.group.parent.remove` | Removes an inheritance parent. | Saves immediately and clears the resolver cache. |
| `/clutchperms users list` | `clutchperms.admin.users.list` | Lists stored subject metadata. | Uses `subjects.json`, not the online player list. |
| `/clutchperms users search <name>` | `clutchperms.admin.users.search` | Searches stored last-known names. | Helps resolve offline targets and ambiguous names. |
| `/clutchperms nodes list` | `clutchperms.admin.nodes.list` | Lists known permission nodes. | Includes built-in, manual, and platform-discovered nodes where available. |
| `/clutchperms nodes search <query>` | `clutchperms.admin.nodes.search` | Searches known nodes and descriptions. | Read-only. |
| `/clutchperms nodes add <node> [description]` | `clutchperms.admin.nodes.add` | Adds or updates a manually known exact permission node. | Writes to `nodes.json`; this is for discovery, suggestions, diagnostics, and Paper wildcard expansion. |
| `/clutchperms nodes remove <node>` | `clutchperms.admin.nodes.remove` | Removes a manually known permission node. | Built-in and platform-discovered nodes are visible but not removable. |

Argument notes:

- `<target>` resolves exact online player name first, then exact stored last-known name, then UUID.
- Ambiguous stored names fail with matching UUIDs instead of choosing one.
- Permission nodes may be exact nodes, `*`, or terminal wildcard nodes like `example.*`. Mid-node wildcards such as `example.*.edit` are rejected.
- Known permission node registry entries must be exact nodes. Wildcards are valid assignments but are not valid known-node registry entries.
- Node registry commands do not grant permissions by themselves.

Wildcard resolution:

- `prefix.*` matches descendants such as `prefix.child` and `prefix.child.deep`, but not `prefix`.
- Matching checks exact nodes first, then the closest wildcard, broader wildcards, and finally `*`.
- Source tiers still win before specificity: direct user assignments beat groups, and groups beat the implicit `default` hierarchy.

Resolver cache:

- Effective permission checks are cached in memory by subject and normalized node.
- Effective permission snapshots are cached by subject for runtime bridge refreshes.
- Direct permission and membership changes invalidate one subject; group definition, group permission, and parent changes clear the cache.
- Reload and restore replace the active resolver with a fresh empty cache.
- Cache state is runtime-only and is not stored in JSON.

## Data Files

ClutchPerms writes four versioned JSON files:

| File | Purpose |
| --- | --- |
| `permissions.json` | Direct user permission assignments |
| `groups.json` | Group definitions, group permissions, parent links, and direct user memberships |
| `subjects.json` | Last-known subject names and last-seen timestamps |
| `nodes.json` | Manually registered exact permission nodes for discovery and wildcard expansion |

Missing storage files load as empty state. After a successful startup or reload, ClutchPerms writes any missing storage files with empty versioned JSON so fresh installs have visible files to inspect and edit. Existing files are never overwritten by this bootstrap step.

Direct permission example:

```json
{
  "version": 1,
  "subjects": {
    "00000000-0000-0000-0000-000000000000": {
      "example.node": "TRUE",
      "example.denied": "FALSE"
    }
  }
}
```

Group example:

```json
{
  "version": 1,
  "groups": {
    "default": {
      "permissions": {
        "example.base": "TRUE"
      }
    },
    "admin": {
      "permissions": {
        "clutchperms.admin.*": "TRUE"
      },
      "parents": [
        "default"
      ]
    }
  },
  "memberships": {
    "00000000-0000-0000-0000-000000000000": [
      "admin"
    ]
  }
}
```

Known node example:

```json
{
  "version": 1,
  "nodes": {
    "example.fly": {
      "description": "Allows example flight."
    },
    "example.build": {}
  }
}
```

Validation is strict. Malformed JSON, unsupported versions, invalid UUIDs, blank names/nodes, unknown permission values, unknown membership groups, explicit `default` memberships, unknown parent groups, and parent cycles fail startup, validate, or reload.
Wildcard keys must be `*` or terminal `prefix.*`; invalid wildcard placement fails startup, validate, or reload.
Known node keys must be exact permission nodes; wildcard known-node entries fail startup, validate, or reload.

### Backups

Before replacing an existing live JSON file, ClutchPerms copies the previous file into a per-file backup directory. The first save of a missing file does not create a backup.

Backup layout:

```text
backups/
  permissions/permissions-YYYYMMDD-HHMMSSSSS.json
  subjects/subjects-YYYYMMDD-HHMMSSSSS.json
  groups/groups-YYYYMMDD-HHMMSSSSS.json
  nodes/nodes-YYYYMMDD-HHMMSSSSS.json
```

Backup root locations match the storage location:

- Paper: plugin data folder, `backups/`
- Fabric, NeoForge, Forge: config dir, `clutchperms/backups/`

ClutchPerms keeps the newest 10 backups per file kind. Backups are recovery snapshots only; they are not migrations, scheduled exports, or a cross-server sync format.

## Forge And NeoForge Activation

Forge and NeoForge allow only one active permission handler. ClutchPerms registers `clutchperms:direct`, but the server config must select it before other mods resolve permissions through ClutchPerms.

NeoForge:

```toml
permissionHandler = "clutchperms:direct"
```

Forge:

```toml
[server]
permissionHandler = "clutchperms:direct"
```

These files are usually generated under the world `serverconfig` directory. Put the same setting under `defaultconfigs` if you want new worlds to inherit it.

Without this setting, `/clutchperms` commands and JSON storage still work, but platform permission checks continue using the default Forge or NeoForge handler.

## Build

Use the Gradle wrapper:

```bash
./gradlew clean build
```

Common development checks:

```bash
./gradlew check
./gradlew spotlessCheck
./gradlew spotlessApply
```

Targeted checks:

```bash
./gradlew :common:test
./gradlew :paper:test
./gradlew :fabric:build
./gradlew :neoforge:build
./gradlew :forge:build
```

The build uses a Java 25 toolchain. The Gradle wrapper is intentionally `9.4.1` for the current Fabric/Loom setup.

Runtime jars are copied to the root `build/` directory:

- `build/clutchperms-paper-0.1.0-SNAPSHOT.jar`
- `build/clutchperms-fabric-0.1.0-SNAPSHOT.jar`
- `build/clutchperms-neoforge-0.1.0-SNAPSHOT.jar`
- `build/clutchperms-forge-0.1.0-SNAPSHOT.jar`

## Project Layout

```text
common/    Shared permission, group, subject, storage, and command code
paper/     Paper plugin adapter and runtime attachment bridge
fabric/    Fabric mod adapter and fabric-permissions-api bridge
neoforge/  NeoForge mod adapter and native permission handler
forge/     Forge mod adapter and native permission handler
```

Important shared packages:

- `common.permission` - direct permissions, wildcard utilities, effective resolution, and permission service factories
- `common.group` - groups, memberships, parent links, group storage, and group observers
- `common.node` - known permission node registry, manual node storage, and node registry observers
- `common.subject` - last-known subject metadata
- `common.storage` - storage exceptions, atomic writes, and backup/restore helpers
- `common.command` - shared Brigadier root wiring, command behavior, command messages, and subcommand branch builders

Version pins live in `gradle.properties`. Module metadata lives in:

- `paper/src/main/resources/plugin.yml`
- `fabric/src/main/resources/fabric.mod.json`
- `neoforge/src/main/resources/META-INF/neoforge.mods.toml`
- `forge/src/main/resources/META-INF/mods.toml`

## Current Targets

| Dependency | Version |
| --- | --- |
| Minecraft | `26.1.2` |
| Paper API | `26.1.2.build.19-alpha` |
| Fabric Loader | `0.19.2` |
| Fabric API | `0.146.1+26.1.2` |
| Fabric Permissions API | `0.7.0` |
| NeoForge | `26.1.2.22-beta` |
| Forge | `64.0.5` |
| Brigadier | `1.3.10` |
| Gson | `2.13.2` |

Paper tests use MockBukkit `4.108.0` with Paper API `1.21.11-R0.1-SNAPSHOT`, which is older than the production Paper compile target. Keep Paper platform code thin when MockBukkit does not cover a newer Paper API.

## Known Limitations

- No contexts
- No group priorities
- No LuckPerms bridge or migration tooling
- No backup compression, encryption, scheduled snapshots, or multi-file restore command
- No cross-server or cross-platform synchronization
- No persisted or externally manageable resolver cache
- Command targets are exact online names, exact stored last-known names, or UUIDs only
- Fabric enforcement only affects mods that query fabric-permissions-api
- Forge and NeoForge enforcement only affects registered Boolean permission nodes and requires `clutchperms:direct` to be selected
- Fabric, NeoForge, and Forge currently have smoke tests rather than full live-server gameplay tests

## License

No standalone license file has been added yet. Forge and NeoForge metadata currently declare `All Rights Reserved`.
