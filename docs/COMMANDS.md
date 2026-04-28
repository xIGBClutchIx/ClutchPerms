# Command Reference

Use `/clutchperms`, `/cperms`, or `/perms`. The tables use `/clutchperms`, but every shared command also works through either alias.

Command help and long list results are paged. In chat, command rows can be clicked to paste a command, page controls move between pages, and hover text shows concise command details.

Console and remote console can run commands for bootstrap. Players need the exact effective command permission for the command they run, and player command trees/completions only expose branches backed by permissions they currently have.

Destructive commands require repeat-command confirmation. Run the same destructive operation again within 30 seconds to confirm `user clear-all`, `group clear-all`, `group delete`, `track delete`, `backup restore`, or audit history prune commands.

## Useful Grants

| Grant | Covers |
| --- | --- |
| `clutchperms.admin.*` | Every ClutchPerms admin command |
| `clutchperms.admin.user.*` | Direct user permissions, user group membership, and user display values |
| `clutchperms.admin.user.track.*` | User track listing plus promote and demote actions |
| `clutchperms.admin.group.*` | Group definitions, group permissions, group members, group parents, and group display values |
| `clutchperms.admin.track.*` | Track definitions and ordered group management |
| `clutchperms.admin.config.*` | Runtime config view, set, and reset |
| `clutchperms.admin.backup.*` | Backup create, list, schedule status, run-now, and restore |
| `clutchperms.admin.users.*` | Stored user list and search |
| `clutchperms.admin.nodes.*` | Known permission node registry |
| `clutchperms.admin.history` | Audit history listing |
| `clutchperms.admin.history.*` | Audit history pruning |
| `clutchperms.admin.undo` | Undoing undoable audit history entries |

`clutchperms.admin` is only the namespace root. It does not grant command access by itself.

## Root And Runtime

| Command | Permission | Description |
| --- | --- | --- |
| `/clutchperms` | `clutchperms.admin.help` | Shows command help page 1. |
| `/clutchperms help [page]` | `clutchperms.admin.help` | Shows paged command help. |
| `/clutchperms status` | `clutchperms.admin.status` | Shows storage paths, config values, counts, resolver cache counts, and runtime bridge status. |
| `/clutchperms reload` | `clutchperms.admin.reload` | Reloads config and database storage, then refreshes runtime permissions. |
| `/clutchperms validate` | `clutchperms.admin.validate` | Parses config and database storage without applying them. |
| `/clutchperms history [page]` | `clutchperms.admin.history` | Lists newest command mutation audit entries. |
| `/clutchperms history prune days <days>` | `clutchperms.admin.history.prune` | Deletes audit entries older than the supplied age after repeat confirmation. |
| `/clutchperms history prune count <count>` | `clutchperms.admin.history.prune` | Keeps only the newest audit entries after repeat confirmation. |
| `/clutchperms undo <id>` | `clutchperms.admin.undo` | Reverts one undoable audit entry if current state still matches the logged after snapshot. |

## Paper Shortcuts

On Paper, ClutchPerms can register unqualified `/op` and `/deop` command roots. These commands replace the Paper command labels with ClutchPerms-backed shortcuts: they add or remove explicit membership in the protected `op` group and do not change Bukkit server-op state or `ops.json`.

This replacement is enabled by default. Set `paper.replaceOpCommands` to `false` before startup to leave Paper's command labels alone.

| Command | Permission | Description |
| --- | --- | --- |
| `/op <target>` | `clutchperms.admin.user.group.add` | Adds a user to the protected `op` group. |
| `/deop <target>` | `clutchperms.admin.user.group.remove` | Removes a user from the protected `op` group. |

## Config

| Command | Permission | Description |
| --- | --- | --- |
| `/clutchperms config list` | `clutchperms.admin.config.view` | Lists active runtime config values. |
| `/clutchperms config get <key>` | `clutchperms.admin.config.view` | Shows one config value. |
| `/clutchperms config set <key> <value>` | `clutchperms.admin.config.set` | Saves one config value, reloads runtime, and rolls back on failure. |
| `/clutchperms config reset <key\|all>` | `clutchperms.admin.config.reset` | Resets one config value or all values to defaults. |

Config keys are documented in [STORAGE.md](STORAGE.md).

## Backups

| Command | Permission | Description |
| --- | --- | --- |
| `/clutchperms backup create` | `clutchperms.admin.backup.create` | Creates a consistent database snapshot. |
| `/clutchperms backup list [page]` | `clutchperms.admin.backup.list` | Lists database backups. |
| `/clutchperms backup list page <page>` | `clutchperms.admin.backup.list` | Lists database backups on a specific page. |
| `/clutchperms backup schedule status` | `clutchperms.admin.backup.list` | Shows automatic backup schedule state and last run/failure details. |
| `/clutchperms backup schedule enable` | `clutchperms.admin.config.set` | Enables automatic database backups. |
| `/clutchperms backup schedule disable` | `clutchperms.admin.config.set` | Disables automatic database backups. |
| `/clutchperms backup schedule interval <minutes>` | `clutchperms.admin.config.set` | Sets automatic backup interval, from 5 to 10080 minutes. |
| `/clutchperms backup schedule run-now` | `clutchperms.admin.backup.create` | Creates an immediate backup, even when automatic backups are disabled. |
| `/clutchperms backup restore <backup-file>` | `clutchperms.admin.backup.restore` | Restores one validated database backup and reloads if valid. |

## User Commands

`<target>` resolves in this order: exact online name, exact resolvable offline name, exact stored last-known name, then UUID.

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
| `/clutchperms user <target> tracks [page]` | `clutchperms.admin.user.track.list` | Lists the user's matching positions across defined tracks. |
| `/clutchperms user <target> track promote <track>` | `clutchperms.admin.user.track.promote` | Moves the user to the next group on a track. |
| `/clutchperms user <target> track demote <track>` | `clutchperms.admin.user.track.demote` | Moves the user to the previous group on a track. |
| `/clutchperms user <target> prefix get` | `clutchperms.admin.user.display.view` | Shows direct and effective user prefix values. |
| `/clutchperms user <target> prefix set <text>` | `clutchperms.admin.user.display.set` | Sets a direct user prefix. |
| `/clutchperms user <target> prefix clear` | `clutchperms.admin.user.display.clear` | Clears the direct user prefix. |
| `/clutchperms user <target> suffix get` | `clutchperms.admin.user.display.view` | Shows direct and effective user suffix values. |
| `/clutchperms user <target> suffix set <text>` | `clutchperms.admin.user.display.set` | Sets a direct user suffix. |
| `/clutchperms user <target> suffix clear` | `clutchperms.admin.user.display.clear` | Clears the direct user suffix. |

## Group Commands

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

## Track Commands

Tracks are ordered group ladders used by the shared promote and demote helpers. They do not affect permission or display resolution by themselves.

`default` may appear only as the first track entry. `op` cannot appear on a track. Manual `user group add` and `user group remove` commands remain free-form; only the track commands enforce ordered-track rules.

| Command | Permission | Description |
| --- | --- | --- |
| `/clutchperms track list [page]` | `clutchperms.admin.track.list` | Lists tracks. |
| `/clutchperms track <track> create` | `clutchperms.admin.track.create` | Creates an empty track. |
| `/clutchperms track <track> delete` | `clutchperms.admin.track.delete` | Deletes a track after repeat confirmation. |
| `/clutchperms track <track> info` | `clutchperms.admin.track.info` | Shows a quick track summary. |
| `/clutchperms track <track> list [page]` | `clutchperms.admin.track.view` | Lists the ordered groups on one track. |
| `/clutchperms track <track> rename <new-track>` | `clutchperms.admin.track.rename` | Renames a track. |
| `/clutchperms track <track> append <group>` | `clutchperms.admin.track.append` | Adds a group at the end of a track. |
| `/clutchperms track <track> insert <position> <group>` | `clutchperms.admin.track.insert` | Inserts a group at a one-based position. |
| `/clutchperms track <track> move <group> <position>` | `clutchperms.admin.track.move` | Moves one track group to a one-based position. |
| `/clutchperms track <track> remove <group>` | `clutchperms.admin.track.remove` | Removes one group from a track and compacts the remaining positions. |

Track promote and demote only count explicit memberships for non-`default` entries. If a user matches multiple explicit groups on the same track, promote and demote fail instead of guessing. When no explicit match exists, promote starts from implicit `default` when the track begins there; otherwise it assigns the first track group. Demote from implicit `default` and promote or demote past either end of a track fail clearly.

## Stored Users

| Command | Permission | Description |
| --- | --- | --- |
| `/clutchperms users list [page]` | `clutchperms.admin.users.list` | Lists stored subject metadata. |
| `/clutchperms users search <name> [page]` | `clutchperms.admin.users.search` | Searches stored last-known names. |

## Known Nodes

| Command | Permission | Description |
| --- | --- | --- |
| `/clutchperms nodes list [page]` | `clutchperms.admin.nodes.list` | Lists known permission nodes. |
| `/clutchperms nodes search <query> [page]` | `clutchperms.admin.nodes.search` | Searches known nodes and descriptions. |
| `/clutchperms nodes add <node> [description]` | `clutchperms.admin.nodes.add` | Adds or updates a manually known exact node. |
| `/clutchperms nodes remove <node>` | `clutchperms.admin.nodes.remove` | Removes a manually known node. |

## Notes

- Page numbers start at 1.
- Invalid or out-of-range pages return styled ClutchPerms feedback and a command to try.
- Bad user, group, backup, and manual-node targets show styled closest matches or a next command to try.
- Ambiguous stored names fail with matching UUIDs instead of choosing one.
- Track names and group names are normalized before storage and lookup.
- On Paper, `/op <target>` and `/deop <target>` are ClutchPerms shortcuts for adding or removing explicit `op` group membership when `paper.replaceOpCommands` is enabled.
- Successful command-layer mutations are written to the SQLite audit log with actor, target, action, timestamp, before/after snapshots, source command, and undo state.
- `/clutchperms undo <id>` refuses to overwrite newer changes: the current target state must still match the audited after snapshot.
