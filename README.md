# ClutchPerms

`ClutchPerms` is a multi-framework Java scaffold for a future permission system that aims to behave consistently across Paper, Fabric, NeoForge, and Forge.

The current repository is intentionally small. It establishes the build, module boundaries, packaging strategy, and the first shared permission API so future work can grow from a clean base instead of mixing platform concerns too early.

## Current Status
- Multi-project Gradle build using Kotlin DSL
- Shared `common` module for platform-agnostic direct permissions, groups, effective resolution, persistence, and commands
- `paper` plugin module with Paper service registration, shared Brigadier commands, and runtime permission attachments
- `fabric` mod module with the same persisted model, shared Brigadier commands, and a fabric-permissions-api provider bridge
- `neoforge` mod module with the same persisted model, shared Brigadier commands, and a native permission handler bridge
- `forge` mod module with the same persisted model, shared Brigadier commands, and a native permission handler bridge
- JUnit coverage for the shared service
- JUnit coverage for the shared command tree
- MockBukkit coverage for the Paper bootstrap, command adapter, and runtime permission bridge path

This is not a production-ready permissions plugin yet. It is the bootstrap layer for one.

## Modules

### `common`
Shared Java library with no Paper, Bukkit, Fabric, or Minecraft dependencies.

Package layout:
- `common.permission` owns direct and effective permission APIs, node normalization, JSON-backed direct permission storage, observing wrappers, and effective resolution
- `common.group` owns basic group APIs, group JSON storage, membership storage, and group mutation notifications
- `common.subject` owns lightweight subject metadata APIs and JSON storage
- `common.storage` owns shared persistence failure types
- `common.command` owns the shared Brigadier `/clutchperms` command tree, command language, and thin platform adapter contract

Current public types:
- `common.permission.PermissionValue`
  - `TRUE`
  - `FALSE`
  - `UNSET`
- `common.permission.PermissionService`
  - `PermissionValue getPermission(UUID subjectId, String node)`
  - `Map<String, PermissionValue> getPermissions(UUID subjectId)`
  - `boolean hasPermission(UUID subjectId, String node)`
  - `void setPermission(UUID subjectId, String node, PermissionValue value)`
  - `void clearPermission(UUID subjectId, String node)`
- `common.permission.PermissionNodes`
  - currently exposes `clutchperms.admin`
- `common.permission.PermissionChangeListener`
  - receives subject-level mutation notifications from observing permission services
- `common.permission.PermissionResolver`
  - resolves effective permissions from direct assignments, explicit groups, and the implicit `default` group
- `common.permission.PermissionResolution`
  - describes an effective value and whether it came from direct, group, default, or unset state
- `common.group.GroupService`
  - stores named groups, group permissions, and direct subject memberships
- `common.group.GroupChangeListener`
  - receives group-level refresh notifications from observing group services
- `common.subject.SubjectMetadata`
  - records a subject UUID, last known name, and last seen timestamp
- `common.subject.SubjectMetadataService`
  - stores lightweight subject metadata keyed by UUID
- `common.command`
  - builds the shared Brigadier `/clutchperms` command tree
  - keeps command authorization and direct user permission behavior platform-neutral
  - uses thin platform adapters for source classification, online player lookup, and message delivery

Current implementation:
- `InMemoryPermissionService`
  - stores permissions per `UUID`
  - normalizes nodes to lower-case
  - removes the entry when a permission is cleared or set back to `UNSET`
- `PermissionServices.jsonFile(Path)`
  - loads and saves direct permission assignments from JSON
  - treats a missing file as empty state
  - saves after every mutation
  - fails startup on malformed or unsupported permission data
- `PermissionServices.observing(PermissionService, PermissionChangeListener)`
  - wraps a service and reports successful direct permission mutations
- `GroupServices.jsonFile(Path)`
  - loads and saves group definitions and memberships from JSON
  - treats a missing file as empty state
  - saves after every mutation
  - fails startup on malformed or unsupported group data
- `GroupServices.observing(GroupService, GroupChangeListener)`
  - wraps a group service and reports successful group and membership mutations
- `SubjectMetadataServices.jsonFile(Path)`
  - loads and saves subject metadata from JSON
  - treats a missing file as empty state
  - saves after every subject observation
  - fails startup on malformed or unsupported subject metadata

### `paper`
Paper plugin module.

Current behavior:
- compiles against the Paper API
- may use Paper-only APIs; Spigot compatibility is not a project goal
- creates a JSON-backed permission service on plugin enable
- registers the shared service through Paper's Bukkit-derived `ServicesManager`
- registers the subject metadata service through Paper's Bukkit-derived `ServicesManager`
- registers the group service and effective permission resolver through Paper's Bukkit-derived `ServicesManager`
- registers `/clutchperms` through Paper lifecycle Brigadier command registration
- exposes status, reload, direct user permission, group, user group, and effective check commands
- bridges effective persisted assignments into Bukkit `PermissionAttachment`s for online players
- records player UUID, name, and last seen time when players join
- stores direct permission assignments in the plugin data folder at `permissions.json`
- stores subject metadata in the plugin data folder at `subjects.json`
- stores group definitions and memberships in the plugin data folder at `groups.json`

Current metadata:
- plugin name: `ClutchPerms`
- permission node: `clutchperms.admin`

Packaging behavior:
- the final Paper jar includes the compiled classes from `common`

### `fabric`
Fabric mod module built with Fabric Loom.

Current behavior:
- creates the same JSON-backed permission and group services during mod initialization
- registers the shared `/clutchperms` command tree with Brigadier through Fabric API
- exposes effective persisted assignments through fabric-permissions-api for mods that query that API
- records player UUID, name, and last seen time when players join
- resets the static service reference when the server stops
- stores direct permission assignments in the Fabric config directory at `clutchperms/permissions.json`
- stores subject metadata in the Fabric config directory at `clutchperms/subjects.json`
- stores group definitions and memberships in the Fabric config directory at `clutchperms/groups.json`

Packaging behavior:
- the final Fabric jar includes `common` as a nested included jar
- the final Fabric jar includes fabric-permissions-api as a nested included jar

### `neoforge`
NeoForge mod module built with ModDevGradle.

Current behavior:
- creates the same JSON-backed permission and group services during mod construction
- registers the shared `/clutchperms` command tree with Brigadier through the NeoForge event bus
- registers a native permission handler as `clutchperms:direct`
- exposes effective persisted assignments for registered Boolean `PermissionNode`s when the server config selects `clutchperms:direct` as the active permission handler
- registers `clutchperms.admin` as a Boolean permission node with default `false`
- records player UUID, name, and last seen time when players join
- resets the static service reference when the server stops
- stores direct permission assignments in the NeoForge config directory at `clutchperms/permissions.json`
- stores subject metadata in the NeoForge config directory at `clutchperms/subjects.json`
- stores group definitions and memberships in the NeoForge config directory at `clutchperms/groups.json`

Packaging behavior:
- the final NeoForge jar includes `common` as a nested jar through NeoForge jar-in-jar metadata

### `forge`
Forge mod module built with ForgeGradle.

Current behavior:
- creates the same JSON-backed permission and group services during mod construction
- registers the shared `/clutchperms` command tree with Brigadier through the Forge event buses
- registers a native permission handler as `clutchperms:direct`
- exposes effective persisted assignments for registered Boolean `PermissionNode`s when the server config selects `clutchperms:direct` as the active permission handler
- registers `clutchperms.admin` as a Boolean permission node with default `false`
- records player UUID, name, and last seen time when players join
- resets the static service reference when the server stops
- stores direct permission assignments in the Forge config directory at `clutchperms/permissions.json`
- stores subject metadata in the Forge config directory at `clutchperms/subjects.json`
- stores group definitions and memberships in the Forge config directory at `clutchperms/groups.json`

Packaging behavior:
- the final Forge jar includes the compiled classes from `common`

## Version Targets

### Primary build targets
- Java toolchain: `25`
- Gradle wrapper: `9.4.1`
- Paper API: `26.1.2.build.19-alpha`
- Minecraft: `26.1.2`
- Fabric Loader: `0.19.2`
- Fabric API: `0.146.1+26.1.2`
- Fabric Permissions API: `0.7.0`
- Loom: `1.16-SNAPSHOT` resolving to `1.16.1`
- NeoForge: `26.1.2.22-beta`
- ModDevGradle: `2.0.141`
- Forge: `64.0.5`
- ForgeGradle: `7.0.25`
- Gson: `2.13.2`
- Brigadier: `1.3.10`

### Test-specific compatibility note
Paper tests use MockBukkit:
- MockBukkit: `4.108.0`
- test Paper API line: `1.21.11-R0.1-SNAPSHOT`

That mismatch is intentional. The production Paper module is compiled against the newer API line, but MockBukkit currently supports the older `1.21.11` Paper line. Paper-only APIs are allowed in production code, but tests may need thin adapters or compile/build coverage when MockBukkit lags behind the current Paper API.

## Getting Started

### Requirements
- JDK 21+ installed locally
- internet access for Gradle dependency resolution

You do not need to preinstall Gradle. Use the wrapper.

### Build Everything
```bash
./gradlew clean build
```

### Run Checks
```bash
./gradlew check
```

### Check Formatting
```bash
./gradlew spotlessCheck
```

### Apply Formatting
```bash
./gradlew spotlessApply
```

### IDE Formatter Config
- Shared Eclipse Java formatter profile: `eclipse-java-formatter.xml`
- Spotless uses that same file for Java formatting, so IDE and build formatting stay aligned.
- The Java import order used by Spotless is:
  - `java`
  - `javax`
  - `org`
  - `com`
  - `io`
  - `me`
  - static imports last

### Run Targeted Tasks
```bash
./gradlew :common:test
./gradlew :paper:test
./gradlew :fabric:build
./gradlew :neoforge:build
./gradlew :forge:build
```

## Output Artifacts
After a successful build, the main artifacts are collected in:
- `build/`

The primary runtime jars are:
- `build/clutchperms-paper-0.1.0-SNAPSHOT.jar`
- `build/clutchperms-fabric-0.1.0-SNAPSHOT.jar`
- `build/clutchperms-neoforge-0.1.0-SNAPSHOT.jar`
- `build/clutchperms-forge-0.1.0-SNAPSHOT.jar`

Only the distributable Paper, Fabric, NeoForge, and Forge runtime jars are copied there. The shared library jar and any `-sources.jar` files remain in their normal module `build/libs` directories.

These are copies of the normal module outputs. The original archives remain in each subproject's `build/libs` directory as well.

The Paper jar includes the shared `common` classes directly.

The Fabric jar contains a nested included jar:
- `META-INF/jars/clutchperms-common-0.1.0-SNAPSHOT.jar`
- `META-INF/jars/fabric-permissions-api-0.7.0.jar`

The NeoForge jar contains a nested jar-in-jar dependency:
- `META-INF/jarjar/me.clutchy.clutchperms.clutchperms-common-0.1.0-SNAPSHOT.jar`

The Forge jar includes the shared `common` classes directly.

## Commands And Behavior

All platforms register the same shared Brigadier command behavior:

```text
/clutchperms
/clutchperms status
/clutchperms reload
/clutchperms user <target> list
/clutchperms user <target> get <node>
/clutchperms user <target> set <node> <true|false>
/clutchperms user <target> clear <node>
/clutchperms user <target> groups
/clutchperms user <target> group add <group>
/clutchperms user <target> group remove <group>
/clutchperms user <target> check <node>
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

Behavior:
- `/clutchperms` lists the available ClutchPerms commands
- `/clutchperms status` shows the permissions file, subjects file, groups file, known-subject count, known-group count, and runtime bridge status
- `/clutchperms reload` reloads `permissions.json`, `subjects.json`, and `groups.json` from disk, then refreshes runtime permission bridges for online subjects where the platform keeps runtime state
- `<target>` resolves an exact online player name first, then an exact stored last-known name, then a UUID string
- ambiguous stored last-known names fail with matching UUIDs instead of choosing one
- UUID targets with recorded subject metadata are displayed as `Name (uuid)` in command feedback
- `<node>` is a single Brigadier word and is normalized by the shared permission service
- `<node>` suggestions include `clutchperms.admin` and effective nodes already assigned to the selected target or selected group
- user `set` writes direct explicit `TRUE` or `FALSE`; user `clear` removes the direct explicit assignment
- group `set` writes group explicit `TRUE` or `FALSE`; group `clear` removes the group explicit assignment
- group names are normalized with `trim().toLowerCase(Locale.ROOT)`
- a group named `default` applies implicitly to every subject when it exists
- users cannot be explicitly added to or removed from `default`
- effective permission checks resolve direct user assignments first, then explicit user groups, then the implicit `default` group
- within the explicit group tier, `FALSE` wins over `TRUE`
- console and remote console sources may run commands for bootstrap
- players must have effective `clutchperms.admin` set to `TRUE`, either directly or through a group
- non-player/non-console sources are denied where the platform adapter can distinguish them
- `users list` shows every subject recorded in `subjects.json`
- `users search <name>` finds recorded subjects by case-insensitive last-known-name substring

## Forge And NeoForge Runtime Bridge Activation

Forge and NeoForge allow only one active permission handler. ClutchPerms registers its handler as `clutchperms:direct`, but the platform server config must select it before other mods will resolve permissions through ClutchPerms.

Without this setting:
- `/clutchperms` commands still work
- JSON permission storage still works
- the ClutchPerms handler is registered but inactive
- platform permission checks keep using the default Forge or NeoForge handler

After the server has generated its platform server config, set the permission handler and restart the server.

For NeoForge, set `permissionHandler` to `clutchperms:direct`:

```toml
permissionHandler = "clutchperms:direct"
```

For Forge, set `server.permissionHandler` to `clutchperms:direct`:

```toml
[server]
permissionHandler = "clutchperms:direct"
```

On dedicated servers, these server config files are typically generated under the world `serverconfig` directory. To make the setting apply to newly created worlds, place the same value in the matching file under `defaultconfigs`.

When active, Forge and NeoForge permission checks for registered Boolean `PermissionNode`s resolve as:
- effective `TRUE` -> `true`
- effective `FALSE` -> `false`
- effective `UNSET` -> the permission node's platform default resolver

## Permission Data

The current persisted permission data model stores only direct permission assignments:
- subject IDs are stored as UUID strings
- permission nodes are normalized with `trim().toLowerCase(Locale.ROOT)`
- only explicit `TRUE` and `FALSE` values are written
- `UNSET` is represented by the absence of an entry
- malformed files, invalid UUIDs, blank nodes, unknown values, or unsupported versions fail startup

Example:
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

## Group Data

The current persisted group model stores basic groups and direct subject memberships:
- group names are normalized with `trim().toLowerCase(Locale.ROOT)`
- group permission nodes use the same normalization and `TRUE`/`FALSE` values as direct permissions
- subject memberships are stored as UUID-to-group-name lists
- `default` is not stored as a membership; when the group exists, it applies to every subject implicitly
- malformed files, invalid UUIDs, blank group names, blank nodes, unknown membership groups, explicit `default` memberships, unknown values, or unsupported versions fail startup

Example:
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

## Subject Metadata

The current subject metadata model stores the latest platform-observed identity for a subject:
- subject IDs are stored as UUID strings
- `lastKnownName` preserves the latest player name observed by the platform
- `lastSeen` is stored as an ISO-8601 instant
- malformed files, invalid UUIDs, blank names, invalid timestamps, or unsupported versions fail startup

Example:
```json
{
  "version": 1,
  "subjects": {
    "00000000-0000-0000-0000-000000000000": {
      "lastKnownName": "ExamplePlayer",
      "lastSeen": "2026-04-24T12:00:00Z"
    }
  }
}
```

## Testing

### Shared logic
`common` has JUnit tests for:
- unset permissions returning `UNSET`
- `hasPermission(...)` behavior
- round-tripping explicit `TRUE` and `FALSE`
- listing explicit normalized permission assignments
- clearing permissions back to `UNSET`
- JSON persistence loading, saving, invalid data handling, and deterministic output
- subject metadata loading, saving, invalid data handling, and deterministic output
- group loading, saving, invalid data handling, deterministic output, membership mutation, observing notifications, and effective resolution
- observing service delegation and mutation notifications
- shared Brigadier command status, authorization, target resolution, mutation, and failure behavior
- shared permission node suggestions for built-in and target-assigned nodes
- shared group commands, user group membership commands, group-based admin authorization, and effective check output
- shared known-user list and search command behavior

### Paper
`paper` has MockBukkit tests for:
- plugin enable
- Paper service registration through the Bukkit-derived service API
- Paper group service and resolver registration
- Paper subject metadata service registration and join-time recording
- the Paper command adapter executing the shared Brigadier tree
- join-time and live runtime permission attachment refresh behavior
- command mutation to JSON storage to runtime attachment behavior for direct and group permissions

### Fabric
`fabric` has smoke tests for the fabric-permissions-api provider bridge:
- effective `TRUE`, `FALSE`, and `UNSET` mapping to Fabric `TriState`
- invalid node fallback to `TriState.DEFAULT`
- command mutation to JSON storage to Fabric `TriState` behavior for direct and group permissions
- malformed permissions and groups reload failure behavior

### NeoForge
`neoforge` has smoke tests for the native permission handler bridge:
- handler identity and registered-node exposure
- effective `TRUE`, `FALSE`, and `UNSET` behavior for Boolean permission nodes
- non-Boolean node fallback to the platform node default
- `clutchperms.admin` Boolean node registration
- command mutation to JSON storage to native permission handler behavior for direct and group permissions
- malformed permissions and groups reload failure behavior

### Forge
`forge` has smoke tests for the native permission handler bridge:
- handler identity and registered-node exposure
- effective `TRUE`, `FALSE`, and `UNSET` behavior for Boolean permission nodes
- non-Boolean node fallback to the platform node default
- `clutchperms.admin` Boolean node registration
- command mutation to JSON storage to native permission handler behavior for direct and group permissions
- malformed permissions and groups reload failure behavior

## Architecture Notes
- `common` is the source of truth for permission abstractions.
- Platform modules should adapt to `common`, not redefine their own permission models.
- Shared behavior should move into `common` unless it depends on platform-specific APIs.
- Paper effective assignments are applied to online players with plugin-owned `PermissionAttachment`s.
- Paper is a Paper-only target; Spigot compatibility is not maintained.
- Fabric effective assignments are provided through fabric-permissions-api as `TriState.TRUE`, `TriState.FALSE`, or `TriState.DEFAULT`.
- NeoForge effective assignments are available through the native permission API when `clutchperms:direct` is selected as the active permission handler.
- Forge effective assignments are available through the native permission API when `clutchperms:direct` is selected as the active permission handler.
- Fabric, NeoForge, and Forge are server-side only for now.

## Known Limitations
- No group inheritance
- No wildcard permissions
- No contexts
- No group priorities
- Command targets are limited to exact online player names, exact stored last-known names, or UUIDs
- Direct user commands only affect direct user assignments; group commands affect group state
- Fabric runtime enforcement only affects mods that query fabric-permissions-api
- NeoForge runtime enforcement only affects registered Boolean `PermissionNode`s and requires the active permission handler config to be `clutchperms:direct`
- Forge runtime enforcement only affects registered Boolean `PermissionNode`s and requires the active permission handler config to be `clutchperms:direct`
- No LuckPerms bridge or migration path yet
- No cross-platform transport or synchronization
- No full gameplay/runtime test suite yet for Fabric, NeoForge, or Forge

## Near-Term Extension Points
Good next steps from this base:
- add lightweight game-test coverage for Fabric, NeoForge, and Forge
- add group inheritance once basic group behavior is stable
- add wildcard permission resolution with documented precedence
- add Fabric integration tests or a lighter server-level verification strategy

## Development Notes
- Use `./gradlew`, not a system Gradle install.
- Formatting is enforced with Spotless.
- The project line-length target is 180 characters.
- Java uses Spotless with Eclipse JDT formatting so the line length is configurable.
- Gradle Kotlin DSL files use Spotless with ktlint and the same 180 character limit.
- The Eclipse formatter profile also joins comment lines where possible, so short manually wrapped comments may be condensed on format.
- If a Paper-related test starts failing during MockBukkit bootstrap, inspect the test Paper API version before assuming the plugin code is wrong.
- If a Paper-only API is not covered by MockBukkit yet, keep the platform adapter small and cover shared behavior in `common`.
- If you change commands, plugin metadata, or permission nodes, update shared command tests, Paper adapter tests, Paper permission metadata when relevant, and this README if the user-facing behavior changed.
- If you change Fabric entrypoints or dependency metadata, update:
  - `fabric/src/main/resources/fabric.mod.json`
- If you change NeoForge entrypoints or dependency metadata, update:
  - `neoforge/src/main/resources/META-INF/neoforge.mods.toml`
- If you change Forge entrypoints or dependency metadata, update:
  - `forge/src/main/resources/META-INF/mods.toml`

## License
No license file has been added yet.
