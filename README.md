# ClutchPerms

ClutchPerms is an early cross-platform Minecraft permissions project for Paper, Fabric, NeoForge, and Forge. It currently provides a shared permission model, JSON storage, Brigadier commands, basic groups, subject metadata, reload support, and platform runtime bridges.

This is a usable prototype, not a mature permissions suite. The project intentionally keeps the model small: direct user permissions, basic groups, an implicit `default` group, and effective permission resolution shared across platforms.

## What Works

- Shared `/clutchperms` command behavior on Paper, Fabric, NeoForge, and Forge
- JSON-backed storage for direct permissions, groups, and subject metadata
- Direct user permission `TRUE` / `FALSE` / `UNSET`
- Basic named groups with direct user membership
- Implicit `default` group when a group named `default` exists
- Effective resolution order: direct user assignment, explicit user groups, then `default`
- `FALSE` wins over `TRUE` within the same group tier
- Reload command for manual JSON edits
- Last-known player name recording and offline name targeting
- Paper runtime bridge using plugin-owned `PermissionAttachment`s
- Fabric runtime bridge through fabric-permissions-api
- Forge and NeoForge runtime handlers through their native permission APIs

## Platform Status

| Platform | Runtime integration | Storage location |
| --- | --- | --- |
| Paper | Applies effective permissions to online players through Bukkit `PermissionAttachment`s | plugin data folder |
| Fabric | Serves effective permissions through fabric-permissions-api | Fabric config dir, `clutchperms/` |
| NeoForge | Registers native handler `clutchperms:direct` | NeoForge config dir, `clutchperms/` |
| Forge | Registers native handler `clutchperms:direct` | Forge config dir, `clutchperms/` |

Paper is a Paper target. Spigot compatibility is not maintained.

## Commands

All platforms expose the same shared command tree:

```text
/clutchperms
/clutchperms status
/clutchperms reload
/clutchperms user <target> list
/clutchperms user <target> get <node>
/clutchperms user <target> set <node> <true|false>
/clutchperms user <target> clear <node>
/clutchperms user <target> check <node>
/clutchperms user <target> groups
/clutchperms user <target> group add <group>
/clutchperms user <target> group remove <group>
/clutchperms group list
/clutchperms group <group> create
/clutchperms group <group> delete
/clutchperms group <group> list
/clutchperms group <group> get <node>
/clutchperms group <group> set <node> <true|false>
/clutchperms group <group> clear <node>
/clutchperms users list
/clutchperms users search <name>
```

Command notes:

- `/clutchperms` lists available commands.
- `/clutchperms status` shows storage paths, known subject count, group count, and runtime bridge status.
- `/clutchperms reload` reloads `permissions.json`, `subjects.json`, and `groups.json`. If any file is invalid, active runtime state is kept unchanged.
- `<target>` resolves exact online player name first, then exact stored last-known name, then UUID.
- Ambiguous stored names fail with matching UUIDs instead of choosing one.
- Console and remote console can run commands for bootstrap.
- Players need effective `clutchperms.admin`, either directly or through a group.
- Non-player/non-console sources are denied where the platform can distinguish them.

## Data Files

ClutchPerms writes three versioned JSON files:

| File | Purpose |
| --- | --- |
| `permissions.json` | Direct user permission assignments |
| `groups.json` | Group definitions, group permissions, and direct user memberships |
| `subjects.json` | Last-known subject names and last-seen timestamps |

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
        "clutchperms.admin": "TRUE"
      }
    }
  },
  "memberships": {
    "00000000-0000-0000-0000-000000000000": [
      "admin"
    ]
  }
}
```

Validation is strict. Malformed JSON, unsupported versions, invalid UUIDs, blank names/nodes, unknown permission values, unknown membership groups, and explicit `default` memberships fail startup or reload.

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

- `common.permission` - direct permissions, effective resolution, and permission service factories
- `common.group` - groups, memberships, group storage, and group observers
- `common.subject` - last-known subject metadata
- `common.storage` - storage exceptions
- `common.command` - shared Brigadier command tree and command messages

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

- No group inheritance
- No wildcard permissions
- No contexts
- No group priorities
- No LuckPerms bridge or migration tooling
- No cross-server or cross-platform synchronization
- Command targets are exact online names, exact stored last-known names, or UUIDs only
- Fabric enforcement only affects mods that query fabric-permissions-api
- Forge and NeoForge enforcement only affects registered Boolean permission nodes and requires `clutchperms:direct` to be selected
- Fabric, NeoForge, and Forge currently have smoke tests rather than full live-server gameplay tests

## License

No standalone license file has been added yet. Forge and NeoForge metadata currently declare `All Rights Reserved`.
