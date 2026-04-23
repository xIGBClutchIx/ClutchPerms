# ClutchPerms

`ClutchPerms` is a multi-framework Java scaffold for a future permission system that aims to behave consistently across Paper/Spigot and Fabric.

The current repository is intentionally small. It establishes the build, module boundaries, packaging strategy, and the first shared permission API so future work can grow from a clean base instead of mixing platform concerns too early.

## Current Status
- Multi-project Gradle build using Kotlin DSL
- Shared `common` module for platform-agnostic permission logic
- `paper` plugin module with Bukkit service registration and a diagnostic command
- `fabric` mod module with the same in-memory service and a diagnostic command
- JUnit coverage for the shared service
- MockBukkit coverage for the Paper bootstrap path

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
  - `boolean hasPermission(UUID subjectId, String node)`
  - `void setPermission(UUID subjectId, String node, PermissionValue value)`
  - `void clearPermission(UUID subjectId, String node)`
- `PermissionNodes`
  - currently exposes `clutchperms.admin`

Current implementation:
- `InMemoryPermissionService`
  - stores permissions per `UUID`
  - normalizes nodes to lower-case
  - removes the entry when a permission is cleared or set back to `UNSET`

### `paper`
Bukkit-safe plugin module.

Current behavior:
- compiles against the Paper API
- avoids Paper-only APIs so the code stays Spigot-safe
- creates an `InMemoryPermissionService` on plugin enable
- registers the shared service in Bukkit `ServicesManager`
- registers `/clutchperms`
- replies with a simple diagnostic message

Current metadata:
- plugin name: `ClutchPerms`
- permission node: `clutchperms.admin`
- command: `/clutchperms`

Packaging behavior:
- the final Paper jar includes the compiled classes from `common`

### `fabric`
Fabric mod module built with Fabric Loom.

Current behavior:
- creates the same `InMemoryPermissionService` during mod initialization
- registers `/clutchperms` with Brigadier through Fabric API
- resets the static service reference when the server stops

Packaging behavior:
- the final Fabric jar includes `common` as a nested included jar

## Version Targets

### Primary build targets
- Java toolchain: `25`
- Gradle wrapper: `9.4.1`
- Paper API: `26.1.2.build.19-alpha`
- Minecraft: `26.1.2`
- Fabric Loader: `0.19.2`
- Fabric API: `0.146.1+26.1.2`
- Loom: `1.16-SNAPSHOT` resolving to `1.16.1`

### Test-specific compatibility note
Paper tests use MockBukkit:
- MockBukkit: `4.108.0`
- test Paper API line: `1.21.11-R0.1-SNAPSHOT`

That mismatch is intentional. The production Paper module is compiled against the newer API line, but MockBukkit currently supports the older `1.21.11` Paper line. The Paper module is deliberately kept Bukkit-safe so this split stays practical.

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
```

## Output Artifacts
After a successful build, the main artifacts are collected in:
- `build/`

The primary runtime jars are:
- `build/clutchperms-paper-0.1.0-SNAPSHOT.jar`
- `build/clutchperms-fabric-0.1.0-SNAPSHOT.jar`

Only the distributable Paper and Fabric runtime jars are copied there. The shared library jar and any `-sources.jar` files remain in their normal module `build/libs` directories.

These are copies of the normal module outputs. The original archives remain in each subproject's `build/libs` directory as well.

The Paper jar includes the shared `common` classes directly.

The Fabric jar contains a nested included jar:
- `META-INF/jars/clutchperms-common-0.1.0-SNAPSHOT.jar`

## Commands And Behavior

### Paper / Spigot
`/clutchperms`
- requires `clutchperms.admin`
- currently returns a diagnostic message indicating that the in-memory permission service is active

### Fabric
`/clutchperms`
- currently returns the same diagnostic message

At this stage, both commands are meant to prove platform bootstrapping and shared behavior, not to provide end-user permission management.

## Testing

### Shared logic
`common` has JUnit tests for:
- unset permissions returning `UNSET`
- `hasPermission(...)` behavior
- round-tripping explicit `TRUE` and `FALSE`
- clearing permissions back to `UNSET`

### Paper
`paper` has MockBukkit tests for:
- plugin enable
- Bukkit service registration
- command registration and command response

### Fabric
There are currently no Fabric runtime tests. The module is verified through compile/build checks only.

## Architecture Notes
- `common` is the source of truth for permission abstractions.
- Platform modules should adapt to `common`, not redefine their own permission models.
- Shared behavior should move into `common` unless it depends on platform-specific APIs.
- Paper stays Bukkit-safe for now.
- Fabric is server-side only for now.

## Known Limitations
- No persistence layer
- No groups or inheritance
- No wildcard permissions
- No contexts
- No LuckPerms bridge or migration path yet
- No cross-platform transport or synchronization
- No Fabric gameplay/runtime test suite yet

## Near-Term Extension Points
Good next steps from this base:
- add a persistence-backed permission store in `common`
- add platform adapters for player lookup and permission resolution
- expose permission inspection and mutation commands
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
- If you change commands, plugin metadata, or permission nodes, update:
  - `paper/src/main/resources/plugin.yml`
  - Paper tests
  - this README if the user-facing behavior changed
- If you change Fabric entrypoints or dependency metadata, update:
  - `fabric/src/main/resources/fabric.mod.json`

## License
No license file has been added yet.
