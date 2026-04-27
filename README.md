# ClutchPerms

ClutchPerms is an early cross-platform Minecraft permissions project for Paper, Fabric, NeoForge, and Forge. It provides shared `/clutchperms` commands, SQLite storage, users, groups, terminal wildcard permissions, prefixes/suffixes, backup/restore support, and runtime permission bridges for each platform.

This is a usable prototype, not a mature permissions suite. The model is intentionally small: direct user permissions, inherited groups, built-in `default` and `op` groups, terminal wildcards, and shared effective permission resolution.

For first install and admin bootstrap, see [SETUP.md](SETUP.md).

## Features

- Shared command tree on Paper, Fabric, NeoForge, and Forge
- Command aliases: `/clutchperms`, `/cperms`, and `/perms`
- `config.json` for runtime config and `database.db` for permissions, groups, subjects, and known nodes
- Direct user permissions with `TRUE`, `FALSE`, and `UNSET`
- Named groups with recursive parent inheritance
- Built-in implicit `default` group and protected explicit-membership `op` group
- Terminal wildcards: `*` and trailing `prefix.*`
- Offline targeting by stored last-known name or UUID
- User and group prefixes/suffixes with ampersand formatting
- Cross-platform chat display as `prefix name suffix: message`
- Validation, reload, manual database snapshots, and restore with rollback
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

## Commands

Use `/clutchperms`, `/cperms`, or `/perms`. The table uses `/clutchperms`, but every command also works through either alias.

Command help and long list results are paged. In chat, command rows can be clicked to paste a command, page controls move between pages, and hover text shows concise command details.

Console and remote console can run commands for bootstrap. Players need the exact effective command permission for the command they run, and player command trees/completions only expose branches backed by permissions they currently have. After permission-affecting ClutchPerms changes, online player command trees are resent so completions update without relogging.

Destructive commands require repeat-command confirmation. Run the same destructive operation again within 30 seconds to confirm `user clear-all`, `group clear-all`, `group delete`, or `backup restore`.

Useful grants:

| Grant | Covers |
| --- | --- |
| `clutchperms.admin.*` | Every ClutchPerms admin command |
| `clutchperms.admin.user.*` | Direct user permissions, user group membership, and user display values |
| `clutchperms.admin.group.*` | Group definitions, group permissions, group members, group parents, and group display values |
| `clutchperms.admin.config.*` | Runtime config view, set, and reset |
| `clutchperms.admin.backup.*` | Backup create, list, and restore |
| `clutchperms.admin.users.*` | Stored user list and search |
| `clutchperms.admin.nodes.*` | Known permission node registry |
| `clutchperms.admin.history` | Audit history listing |
| `clutchperms.admin.undo` | Undoing undoable audit history entries |

`clutchperms.admin` is only the namespace root. It does not grant command access by itself.

### Root And Runtime

| Command | Permission | Description |
| --- | --- | --- |
| `/clutchperms` | `clutchperms.admin.help` | Shows command help page 1. |
| `/clutchperms help [page]` | `clutchperms.admin.help` | Shows paged command help. |
| `/clutchperms status` | `clutchperms.admin.status` | Shows storage paths, config values, counts, resolver cache counts, and runtime bridge status. |
| `/clutchperms reload` | `clutchperms.admin.reload` | Reloads config and database storage, then refreshes runtime permissions. |
| `/clutchperms validate` | `clutchperms.admin.validate` | Parses config and database storage without applying them. |
| `/clutchperms history [page]` | `clutchperms.admin.history` | Lists newest command mutation audit entries. |
| `/clutchperms undo <id>` | `clutchperms.admin.undo` | Reverts one undoable audit entry if current state still matches the logged after snapshot. |

### Paper Shortcuts

On Paper, ClutchPerms can register unqualified `/op` and `/deop` command roots. These commands replace the Paper command labels with ClutchPerms-backed shortcuts: they add or remove explicit membership in the protected `op` group and do not change Bukkit server-op state or `ops.json`.

This replacement is enabled by default. Set `paper.replaceOpCommands` to `false` before startup to leave Paper's command labels alone.

| Command | Permission | Description |
| --- | --- | --- |
| `/op <target>` | `clutchperms.admin.user.group.add` | Adds a user to the protected `op` group. |
| `/deop <target>` | `clutchperms.admin.user.group.remove` | Removes a user from the protected `op` group. |

### Config

| Command | Permission | Description |
| --- | --- | --- |
| `/clutchperms config list` | `clutchperms.admin.config.view` | Lists active runtime config values. |
| `/clutchperms config get <key>` | `clutchperms.admin.config.view` | Shows one config value. |
| `/clutchperms config set <key> <value>` | `clutchperms.admin.config.set` | Saves one config value, reloads runtime, and rolls back on failure. |
| `/clutchperms config reset <key\|all>` | `clutchperms.admin.config.reset` | Resets one config value or all values to defaults. |

### Backups

| Command | Permission | Description |
| --- | --- | --- |
| `/clutchperms backup create` | `clutchperms.admin.backup.create` | Creates a consistent database snapshot. |
| `/clutchperms backup list [page]` | `clutchperms.admin.backup.list` | Lists database backups. |
| `/clutchperms backup list page <page>` | `clutchperms.admin.backup.list` | Lists database backups on a specific page. |
| `/clutchperms backup restore <backup-file>` | `clutchperms.admin.backup.restore` | Restores one validated database backup and reloads if valid. |

### User Commands

`<target>` is an exact online name, exact stored last-known name, or UUID.

| Command | Permission | Description |
| --- | --- | --- |
| `/clutchperms user <target> info` | `clutchperms.admin.user.info` | Shows a quick user summary. |
| `/clutchperms user <target> list [page]` | `clutchperms.admin.user.list` | Lists direct permissions and display metadata for a user. |
| `/clutchperms user <target> get <node>` | `clutchperms.admin.user.get` | Shows one direct user permission. |
| `/clutchperms user <target> set <node> <true\|false>` | `clutchperms.admin.user.set` | Sets one direct user permission. |
| `/clutchperms user <target> clear <node>` | `clutchperms.admin.user.clear` | Removes one direct user permission. |
| `/clutchperms user <target> clear-all` | `clutchperms.admin.user.clear-all` | Removes every direct user permission. |
| `/clutchperms user <target> check <node>` | `clutchperms.admin.user.check` | Shows the effective permission result. |
| `/clutchperms user <target> explain <node>` | `clutchperms.admin.user.explain` | Explains matching assignments and the winner. |
| `/clutchperms user <target> groups [page]` | `clutchperms.admin.user.groups` | Lists explicit group memberships, including `op` when assigned, and the implicit `default` group. |
| `/clutchperms user <target> group add <group>` | `clutchperms.admin.user.group.add` | Adds an explicit group membership. |
| `/clutchperms user <target> group remove <group>` | `clutchperms.admin.user.group.remove` | Removes an explicit group membership. |
| `/clutchperms user <target> prefix get` | `clutchperms.admin.user.display.view` | Shows direct and effective user prefix values. |
| `/clutchperms user <target> prefix set <text>` | `clutchperms.admin.user.display.set` | Sets a direct user prefix. |
| `/clutchperms user <target> prefix clear` | `clutchperms.admin.user.display.clear` | Clears the direct user prefix. |
| `/clutchperms user <target> suffix get` | `clutchperms.admin.user.display.view` | Shows direct and effective user suffix values. |
| `/clutchperms user <target> suffix set <text>` | `clutchperms.admin.user.display.set` | Sets a direct user suffix. |
| `/clutchperms user <target> suffix clear` | `clutchperms.admin.user.display.clear` | Clears the direct user suffix. |

### Group Commands

The built-in `default` group always exists, applies implicitly to every subject, and cannot be deleted or renamed. The protected built-in `op` group also always exists, grants `* = TRUE`, has no members by default, and only affects users explicitly added to it.

| Command | Permission | Description |
| --- | --- | --- |
| `/clutchperms group list [page]` | `clutchperms.admin.group.list` | Lists groups. |
| `/clutchperms group <group> create` | `clutchperms.admin.group.create` | Creates a group. |
| `/clutchperms group <group> delete` | `clutchperms.admin.group.delete` | Deletes a group and related links. |
| `/clutchperms group <group> info` | `clutchperms.admin.group.info` | Shows a quick group summary. |
| `/clutchperms group <group> rename <new-group>` | `clutchperms.admin.group.rename` | Renames a group and updates related links. |
| `/clutchperms group <group> list [page]` | `clutchperms.admin.group.view` | Lists group permissions, display metadata, parents, and members. |
| `/clutchperms group <group> members [page]` | `clutchperms.admin.group.members` | Lists explicit group members. |
| `/clutchperms group <group> get <node>` | `clutchperms.admin.group.get` | Shows one direct group permission. |
| `/clutchperms group <group> set <node> <true\|false>` | `clutchperms.admin.group.set` | Sets one direct group permission. |
| `/clutchperms group <group> clear <node>` | `clutchperms.admin.group.clear` | Removes one direct group permission. |
| `/clutchperms group <group> clear-all` | `clutchperms.admin.group.clear-all` | Removes every direct group permission. |
| `/clutchperms group <group> prefix get` | `clutchperms.admin.group.display.view` | Shows a group prefix. |
| `/clutchperms group <group> prefix set <text>` | `clutchperms.admin.group.display.set` | Sets a group prefix. |
| `/clutchperms group <group> prefix clear` | `clutchperms.admin.group.display.clear` | Clears a group prefix. |
| `/clutchperms group <group> suffix get` | `clutchperms.admin.group.display.view` | Shows a group suffix. |
| `/clutchperms group <group> suffix set <text>` | `clutchperms.admin.group.display.set` | Sets a group suffix. |
| `/clutchperms group <group> suffix clear` | `clutchperms.admin.group.display.clear` | Clears a group suffix. |
| `/clutchperms group <group> parents [page]` | `clutchperms.admin.group.parents` | Lists parent groups. |
| `/clutchperms group <group> parent add <parent>` | `clutchperms.admin.group.parent.add` | Adds an inheritance parent. |
| `/clutchperms group <group> parent remove <parent>` | `clutchperms.admin.group.parent.remove` | Removes an inheritance parent. |

### Stored Users

| Command | Permission | Description |
| --- | --- | --- |
| `/clutchperms users list [page]` | `clutchperms.admin.users.list` | Lists stored subject metadata. |
| `/clutchperms users search <name> [page]` | `clutchperms.admin.users.search` | Searches stored last-known names. |

### Known Nodes

| Command | Permission | Description |
| --- | --- | --- |
| `/clutchperms nodes list [page]` | `clutchperms.admin.nodes.list` | Lists known permission nodes. |
| `/clutchperms nodes search <query> [page]` | `clutchperms.admin.nodes.search` | Searches known nodes and descriptions. |
| `/clutchperms nodes add <node> [description]` | `clutchperms.admin.nodes.add` | Adds or updates a manually known exact node. |
| `/clutchperms nodes remove <node>` | `clutchperms.admin.nodes.remove` | Removes a manually known node. |

Notes:

- `<target>` resolves exact online name, then exact stored last-known name, then UUID.
- Config keys are `backups.retentionLimit`, `commands.helpPageSize`, `commands.resultPageSize`, `chat.enabled`, and `paper.replaceOpCommands`.
- Config changes apply immediately through save-and-reload. If reload fails, `config.json` is rolled back.
- Page numbers start at 1. Invalid or out-of-range pages return styled ClutchPerms feedback and a command to try.
- Bad user, group, backup, and manual-node targets show styled closest matches or a next command to try.
- Ambiguous stored names fail with matching UUIDs instead of choosing one.
- The `default` group always exists, applies implicitly to every subject, and cannot be deleted or renamed.
- Users cannot be explicitly added to or removed from `default`.
- The `op` group always exists, grants `* = TRUE` to explicit members only, and rejects definition, permission, display, and parent-link edits.
- On Paper, `/op <target>` and `/deop <target>` are ClutchPerms shortcuts for adding or removing explicit `op` group membership when `paper.replaceOpCommands` is enabled. They do not write Bukkit server-op state.
- Display text accepts `&0-9`, `&a-f`, `&k-o`, `&r`, and `&&`; raw section signs, blank values, invalid codes, and values over 128 raw characters are rejected.
- Permission assignments may use exact nodes, `*`, or terminal wildcards like `example.*`.
- Mid-node wildcards such as `example.*.edit` are rejected.
- Known node registry entries must be exact nodes. Wildcards are valid assignments, not known-node entries.
- Successful command-layer mutations are written to the SQLite audit log with actor, target, action, timestamp, before/after snapshots, source command, and undo state. Audit history is retained indefinitely in this version.
- `/clutchperms undo <id>` refuses to overwrite newer changes: the current target state must still match the audited after snapshot. Undo writes its own non-undoable audit entry and marks the original entry undone.
- Backup create/restore, manual known-node changes, player-observation metadata updates, and internal service calls outside commands are not audited in this version.

## Storage

ClutchPerms writes one config file and one SQLite database:

| File | Purpose |
| --- | --- |
| `config.json` | Runtime settings for backup retention, command page sizes, and chat formatting |
| `database.db` | Direct permissions, group definitions, memberships, subject metadata, display values, manually registered known nodes, and command audit history |

Default `config.json`:

```json
{
  "version": 1,
  "backups": {
    "retentionLimit": 10
  },
  "commands": {
    "helpPageSize": 7,
    "resultPageSize": 8
  },
  "chat": {
    "enabled": true
  },
  "paper": {
    "replaceOpCommands": true
  }
}
```

Missing database storage loads as empty state with the built-in `default` and `op` groups. Missing `config.json` loads defaults. Missing files are materialized after successful startup or reload.

Validation is strict. Malformed config, unsupported schema versions, invalid config keys or values, invalid UUIDs, blank names or nodes, duplicate normalized permission keys, invalid wildcard placement, unknown permission values, unknown groups, explicit `default` memberships, invalid protected `op` definitions, unknown parent groups, and parent cycles fail startup, validate, or reload.

Display values are stored in the database as ampersand-formatted strings. Direct user display values are keyed by UUID, and group display values are keyed by normalized group name.

Effective prefix and suffix are resolved independently: direct user value first, then the nearest explicit group hierarchy, then the nearest `default` group hierarchy. If multiple groups at the same depth provide a value, the alphabetically first group name wins.

Chat display is active by default on all supported platforms, can be toggled with `chat.enabled`, and renders as `prefix name suffix: message`. When no prefix or suffix is set, the output stays close to vanilla chat. Because ClutchPerms formats the full chat line, some loaders or clients may treat the line as modified or unsigned.

Backups are manual whole-database snapshots created with `/clutchperms backup create`.

Backup restore validates the selected backup file before replacing live storage. ClutchPerms closes the active SQLite pool, replaces `database.db`, removes stale WAL/SHM sidecar files, reloads config and database storage, and rolls the restored database back if reload fails. `config.json` is not included in backup restore.

If a database mutation cannot commit, ClutchPerms leaves both the live database and the in-memory runtime state unchanged.

```text
backups/
  database/database-YYYYMMDD-HHMMSSSSS.db
```

By default, ClutchPerms keeps the newest 10 database backups. Use `/clutchperms config set backups.retentionLimit <value>` or edit `config.json` to keep between 1 and 1000 backups. Use `/clutchperms config set chat.enabled off` to let the platform handle chat normally. Use `/clutchperms config set paper.replaceOpCommands off` or edit `config.json` to disable Paper's `/op` and `/deop` replacements.

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
