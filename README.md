# ClutchPerms

`ClutchPerms` is a multi-framework Java scaffold for a future permission system that aims to behave consistently across Paper, Fabric, NeoForge, and Forge.

The current repository is intentionally small. It establishes the build, module boundaries, packaging strategy, and the first shared permission API so future work can grow from a clean base instead of mixing platform concerns too early.

## Current Status
- Multi-project Gradle build using Kotlin DSL
- Shared `common` module for platform-agnostic permission logic
- `paper` plugin module with Paper service registration, shared Brigadier commands, and runtime permission attachments
- `fabric` mod module with the same persisted service, shared Brigadier commands, and a fabric-permissions-api provider bridge
- `neoforge` mod module with the same persisted service, shared Brigadier commands, and a native permission handler bridge
- `forge` mod module with the same persisted service, shared Brigadier commands, and a native permission handler bridge
- JUnit coverage for the shared service
- JUnit coverage for the shared command tree
- MockBukkit coverage for the Paper bootstrap, command adapter, and runtime permission bridge path

This is not a production-ready permissions plugin yet. It is the bootstrap layer for one.

## Modules

### `common`
Shared Java library with no Paper, Bukkit, Fabric, or Minecraft dependencies.

Current public types:
- `PermissionValue`
  - `TRUE`
  - `FALSE`
  - `UNSET`
- `PermissionService`
  - `PermissionValue getPermission(UUID subjectId, String node)`
  - `Map<String, PermissionValue> getPermissions(UUID subjectId)`
  - `boolean hasPermission(UUID subjectId, String node)`
  - `void setPermission(UUID subjectId, String node, PermissionValue value)`
  - `void clearPermission(UUID subjectId, String node)`
- `PermissionNodes`
  - currently exposes `clutchperms.admin`
- `PermissionChangeListener`
  - receives subject-level mutation notifications from observing permission services
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

### `paper`
Paper plugin module.

Current behavior:
- compiles against the Paper API
- may use Paper-only APIs; Spigot compatibility is not a project goal
- creates a JSON-backed permission service on plugin enable
- registers the shared service through Paper's Bukkit-derived `ServicesManager`
- registers `/clutchperms` through Paper lifecycle Brigadier command registration
- exposes status plus direct online-player-or-UUID permission get/list/set/clear commands
- bridges direct persisted assignments into Bukkit `PermissionAttachment`s for online players
- stores direct permission assignments in the plugin data folder at `permissions.json`

Current metadata:
- plugin name: `ClutchPerms`
- permission node: `clutchperms.admin`

Packaging behavior:
- the final Paper jar includes the compiled classes from `common`

### `fabric`
Fabric mod module built with Fabric Loom.

Current behavior:
- creates the same JSON-backed permission service during mod initialization
- registers the shared `/clutchperms` command tree with Brigadier through Fabric API
- exposes direct persisted assignments through fabric-permissions-api for mods that query that API
- resets the static service reference when the server stops
- stores direct permission assignments in the Fabric config directory at `clutchperms/permissions.json`

Packaging behavior:
- the final Fabric jar includes `common` as a nested included jar
- the final Fabric jar includes fabric-permissions-api as a nested included jar

### `neoforge`
NeoForge mod module built with ModDevGradle.

Current behavior:
- creates the same JSON-backed permission service during mod construction
- registers the shared `/clutchperms` command tree with Brigadier through the NeoForge event bus
- registers a native permission handler as `clutchperms:direct`
- exposes direct persisted assignments for registered Boolean `PermissionNode`s when the server config selects `clutchperms:direct` as the active permission handler
- registers `clutchperms.admin` as a Boolean permission node with default `false`
- resets the static service reference when the server stops
- stores direct permission assignments in the NeoForge config directory at `clutchperms/permissions.json`

Packaging behavior:
- the final NeoForge jar includes `common` as a nested jar through NeoForge jar-in-jar metadata

### `forge`
Forge mod module built with ForgeGradle.

Current behavior:
- creates the same JSON-backed permission service during mod construction
- registers the shared `/clutchperms` command tree with Brigadier through the Forge event buses
- registers a native permission handler as `clutchperms:direct`
- exposes direct persisted assignments for registered Boolean `PermissionNode`s when the server config selects `clutchperms:direct` as the active permission handler
- registers `clutchperms.admin` as a Boolean permission node with default `false`
- resets the static service reference when the server stops
- stores direct permission assignments in the Forge config directory at `clutchperms/permissions.json`

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
/clutchperms user <target> list
/clutchperms user <target> get <node>
/clutchperms user <target> set <node> <true|false>
/clutchperms user <target> clear <node>
```

Behavior:
- `/clutchperms` returns `ClutchPerms is running with a persisted permission service.`
- `<target>` resolves an exact online player name first, then a UUID string
- `<node>` is a single Brigadier word and is normalized by the shared permission service
- `set` writes explicit `TRUE` or `FALSE`; `clear` removes the explicit assignment
- console and remote console sources may run commands for bootstrap
- players must have the persisted `clutchperms.admin` node set to `TRUE`
- non-player/non-console sources are denied where the platform adapter can distinguish them

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
- persisted `TRUE` -> `true`
- persisted `FALSE` -> `false`
- `UNSET` -> the permission node's platform default resolver

## Permission Data

The current persisted data model stores only direct permission assignments:
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

## Testing

### Shared logic
`common` has JUnit tests for:
- unset permissions returning `UNSET`
- `hasPermission(...)` behavior
- round-tripping explicit `TRUE` and `FALSE`
- listing explicit normalized permission assignments
- clearing permissions back to `UNSET`
- JSON persistence loading, saving, invalid data handling, and deterministic output
- observing service delegation and mutation notifications
- shared Brigadier command status, authorization, target resolution, mutation, and failure behavior

### Paper
`paper` has MockBukkit tests for:
- plugin enable
- Paper service registration through the Bukkit-derived service API
- the Paper command adapter executing the shared Brigadier tree
- join-time and live runtime permission attachment refresh behavior

### Fabric
`fabric` has smoke tests for the fabric-permissions-api provider bridge:
- direct `TRUE`, `FALSE`, and `UNSET` mapping to Fabric `TriState`
- invalid node fallback to `TriState.DEFAULT`

### NeoForge
`neoforge` has smoke tests for the native permission handler bridge:
- handler identity and registered-node exposure
- direct `TRUE`, `FALSE`, and `UNSET` behavior for Boolean permission nodes
- non-Boolean node fallback to the platform node default
- `clutchperms.admin` Boolean node registration

### Forge
`forge` has smoke tests for the native permission handler bridge:
- handler identity and registered-node exposure
- direct `TRUE`, `FALSE`, and `UNSET` behavior for Boolean permission nodes
- non-Boolean node fallback to the platform node default
- `clutchperms.admin` Boolean node registration

## Architecture Notes
- `common` is the source of truth for permission abstractions.
- Platform modules should adapt to `common`, not redefine their own permission models.
- Shared behavior should move into `common` unless it depends on platform-specific APIs.
- Paper direct user assignments are applied to online players with plugin-owned `PermissionAttachment`s.
- Paper is a Paper-only target; Spigot compatibility is not maintained.
- Fabric direct user assignments are provided through fabric-permissions-api as `TriState.TRUE`, `TriState.FALSE`, or `TriState.DEFAULT`.
- NeoForge direct user assignments are available through the native permission API when `clutchperms:direct` is selected as the active permission handler.
- Forge direct user assignments are available through the native permission API when `clutchperms:direct` is selected as the active permission handler.
- Fabric, NeoForge, and Forge are server-side only for now.

## Known Limitations
- No groups or inheritance
- No wildcard permissions
- No contexts
- Command targets are limited to exact online player names or UUIDs
- Command permission changes only affect direct user assignments
- Fabric runtime enforcement only affects mods that query fabric-permissions-api
- NeoForge runtime enforcement only affects registered Boolean `PermissionNode`s and requires the active permission handler config to be `clutchperms:direct`
- Forge runtime enforcement only affects registered Boolean `PermissionNode`s and requires the active permission handler config to be `clutchperms:direct`
- No LuckPerms bridge or migration path yet
- No cross-platform transport or synchronization
- No full gameplay/runtime test suite yet for Fabric, NeoForge, or Forge

## Near-Term Extension Points
Good next steps from this base:
- add lightweight game-test coverage for Fabric, NeoForge, and Forge
- add safer command ergonomics such as tab-completed permission nodes and clearer error messages
- define a cross-platform data model for users, groups, and inheritance
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
