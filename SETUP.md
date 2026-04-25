# ClutchPerms Setup Guide

This guide covers first install, first admin access, basic verification, and the most common platform-specific setup steps. For the full command reference and storage schema notes, see [README.md](README.md).

ClutchPerms is currently a usable prototype. Treat setup as a direct JSON-backed permissions service with shared `/clutchperms` commands, groups, inheritance, wildcards, prefixes/suffixes, reload, validation, backups, and platform runtime bridges.

## 1. Pick The Correct Jar

For normal server installs, download the jar for your platform from [GitHub Releases](https://github.com/xIGBClutchIx/ClutchPerms/releases).

Release assets should use these names:

```text
clutchperms-paper-<version>.jar
clutchperms-fabric-<version>.jar
clutchperms-neoforge-<version>.jar
clutchperms-forge-<version>.jar
```

Install the jar that matches your server:

| Platform | Install location |
| --- | --- |
| Paper | `plugins/` |
| Fabric | `mods/` |
| NeoForge | `mods/` |
| Forge | `mods/` |

Paper is the supported Bukkit-family target. Spigot compatibility is not maintained.

If you are building from source instead of downloading a release, run:

```bash
./gradlew clean build
```

The built jars are copied into `build/` with the same platform-specific names.

## 2. Start The Server Once

Start the server once with ClutchPerms installed. Missing storage files are treated as empty state and are created with versioned JSON after a successful startup. Missing `config.json` uses defaults, and `groups.json` starts with the built-in `default` group.

Storage locations:

| Platform | Storage directory |
| --- | --- |
| Paper | `plugins/ClutchPerms/` |
| Fabric | `config/clutchperms/` |
| NeoForge | `config/clutchperms/` |
| Forge | `config/clutchperms/` |

Expected storage files:

```text
config.json
permissions.json
groups.json
subjects.json
nodes.json
```

`config.json` is created with defaults for backup retention, command page sizes, and chat formatting. You can manage these values in-game with `clutchperms config`, or edit the file manually and run `clutchperms validate` before `clutchperms reload`.

Direct user prefixes/suffixes are stored in `subjects.json`. Group prefixes/suffixes are stored in `groups.json`.

The `backups/` directory is created later when ClutchPerms replaces an existing storage file.

Run this from the server console to confirm ClutchPerms loaded:

```text
clutchperms status
```

The status output should show storage paths, active config values, subject/group/node counts, resolver cache counts, and runtime bridge status.

## 3. Bootstrap The First Admin

Players need effective ClutchPerms command permissions to run ClutchPerms commands. For first setup, create an `owner` group with `*` so the first trusted admin can manage every ClutchPerms command and any early permission checks. Being a Paper op is not enough for ClutchPerms command authorization because commands use the shared ClutchPerms resolver.

Use the server console for the first grant:

```text
clutchperms group owner create
clutchperms group owner set * true
clutchperms user ExamplePlayer group add owner
```

`ExamplePlayer` can be:

- an exact online player name
- an exact stored last-known player name after that player has joined once
- a UUID

If the player has never joined and no last-known name exists, use the UUID:

```text
clutchperms user 00000000-0000-0000-0000-000000000000 group add owner
```

Verify the grant:

```text
clutchperms user ExamplePlayer check clutchperms.admin.status
clutchperms user ExamplePlayer explain clutchperms.admin.status
```

After this, that player should be able to run:

```text
/clutchperms status
```

## 4. Configure The Default Group

The `default` group always exists and applies to every subject automatically. It cannot be deleted, and users cannot be manually added to or removed from it.

Example:

```text
clutchperms group default set example.use true
```

Check a player:

```text
clutchperms user ExamplePlayer check example.use
```

## 5. Add More Groups

Groups can inherit multiple parent groups. Child group permissions beat inherited parent permissions. At the same inheritance depth, `false` wins over `true`.

Example:

```text
clutchperms group staff create
clutchperms group staff set example.moderate true
clutchperms group owner parent add staff
```

List group state:

```text
clutchperms group owner list
clutchperms group owner parents
```

## 6. Add Prefixes And Suffixes

Prefixes and suffixes use ampersand formatting codes: `&0-9`, `&a-f`, `&k-o`, `&r`, and `&&` for a literal ampersand. Raw section signs, invalid codes, blank values, and values over 128 raw characters are rejected.

Examples:

```text
clutchperms group default prefix set &7[Member]
clutchperms group staff prefix set &b[Staff]
clutchperms user ExamplePlayer suffix set &f*
```

Check display state:

```text
clutchperms user ExamplePlayer prefix get
clutchperms user ExamplePlayer suffix get
clutchperms group staff list
```

Effective prefix and suffix resolve independently: direct user value first, then the nearest explicit group hierarchy, then the nearest `default` group hierarchy. If multiple groups at the same depth provide a value, the alphabetically first group name wins.

Chat display is active by default on Paper, Fabric, NeoForge, and Forge, can be toggled with `chat.enabled`, and renders as `prefix name suffix: message`. Without a configured prefix or suffix, chat remains close to vanilla output. Some loaders or clients may mark formatted chat as modified or unsigned.

Clear display values with:

```text
clutchperms user ExamplePlayer suffix clear
clutchperms group staff prefix clear
```

## 7. Register Known Permission Nodes

Unknown permission nodes are still assignable. Known nodes improve suggestions, diagnostics, and Paper wildcard expansion.

Add a manual known node:

```text
clutchperms nodes add example.fly Allows example flight.
clutchperms nodes list
```

Known node entries must be exact permission nodes. Wildcards such as `example.*` are valid permission assignments, but they are not valid known-node registry entries.

## 8. Use Wildcards Carefully

Supported wildcard assignments are:

- `*`
- terminal `prefix.*`

Examples:

```text
clutchperms group staff set example.* true
clutchperms user ExamplePlayer check example.fly
```

`example.*` matches `example.fly` and `example.fly.fast`, but it does not match `example` itself. Mid-node wildcards such as `example.*.fast` are invalid.

Paper note: ClutchPerms expands wildcard assignments onto exact known permission nodes. Arbitrary unregistered Bukkit permission strings are not expanded unless they are known through Paper's registry, ClutchPerms built-ins, or `nodes.json`.

## 9. Validate, Reload, And Recover

Validate manual JSON/config edits without applying them:

```text
clutchperms validate
```

Reload config and all storage files after manual edits:

```text
clutchperms reload
```

Reload is atomic from the command perspective. If any file is invalid, ClutchPerms keeps the active runtime state unchanged.

View or change config from console or in-game:

```text
clutchperms config list
clutchperms config get commands.resultPageSize
clutchperms config set commands.resultPageSize 12
clutchperms config set chat.enabled off
clutchperms config reset commands.resultPageSize
```

Config command changes save `config.json`, reload runtime immediately, and roll the config file back if reload fails.

List backups:

```text
clutchperms backup list
clutchperms backup list groups
```

Restore one file from a backup:

```text
clutchperms backup restore groups groups-YYYYMMDD-HHMMSSSSS.json
```

Restore validates the selected backup before replacing the live file, then reloads config and all storage immediately. If reload fails after replacement, ClutchPerms rolls the disk file back and keeps the active runtime state unchanged. `config.json` is not restored by backup commands in this version.

## 10. Enable Forge And NeoForge Runtime Checks

Forge and NeoForge allow only one active permission handler. ClutchPerms commands and storage work after install, but other mods will not resolve permissions through ClutchPerms until the server selects the `clutchperms:direct` handler.

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

After changing the handler, restart the server and run:

```text
clutchperms status
```

The runtime bridge line should report that the ClutchPerms handler is active.

## 11. Common First-Setup Problems

### A Player Cannot Run `/clutchperms`

Run this from console:

```text
clutchperms user ExamplePlayer check clutchperms.admin.status
clutchperms user ExamplePlayer explain clutchperms.admin.status
```

If the result is `UNSET` or `FALSE`, add the player to the owner group:

```text
clutchperms user ExamplePlayer group add owner
```

### A Name Is Not Found

Target resolution checks exact online player name first, exact stored last-known name second, then UUID. If a player has never joined, use their UUID.

### Runtime Permissions Do Not Affect Forge Or NeoForge Mods

Check that the server selected `clutchperms:direct` as the active permission handler. Without that setting, commands and JSON storage still work, but Forge or NeoForge permission checks use the platform default handler.

### A JSON Or Config Edit Broke Reload

Use:

```text
clutchperms validate
clutchperms backup list
```

Then either fix the JSON/config file and run `clutchperms reload`, or restore a backup for the broken storage file.
