# AGENTS.md

## Purpose
- `ClutchPerms` is a multi-framework Java project intended to grow into a shared permission system that works across Paper, Fabric, NeoForge, and Forge.
- The current state is an early persisted prototype, not a finished permission platform.
- The repo currently provides:
  - a shared `common` module with a minimal permission API, in-memory implementation, and JSON-backed persistence factory
  - a `paper` plugin module with a diagnostic `/clutchperms` command and Paper service registration
  - a `fabric` mod module with a diagnostic `/clutchperms` command and the same shared service
  - a `neoforge` mod module with a diagnostic `/clutchperms` command and the same shared service
  - a `forge` mod module with a diagnostic `/clutchperms` command and the same shared service

## Repository Layout
- `common`
  - pure Java shared code
  - no Bukkit, Paper, Fabric, NeoForge, Forge, or Minecraft dependencies
  - owns the public permission model for now
- `paper`
  - Paper server plugin
  - compiles against Paper API
  - embeds `common` classes directly into the produced jar
  - uses `plugin.yml`, not `paper-plugin.yml`
  - may use Paper-only APIs; Spigot compatibility is not a project goal
- `fabric`
  - Fabric mod built with Loom
  - bundles `common` as a nested jar via `include(project(":common"))`
- `neoforge`
  - NeoForge mod built with ModDevGradle
  - bundles `common` as a nested jar via NeoForge jar-in-jar metadata
- `forge`
  - Forge mod built with ForgeGradle
  - embeds `common` classes directly into the produced jar
- root build
  - Gradle Kotlin DSL multi-project setup
  - centralized Java toolchain and test configuration

## Current Functional Scope
- Shared public API:
  - `PermissionService`
  - `PermissionValue`
  - `PermissionNodes`
- Current backends:
  - `InMemoryPermissionService`
  - `PermissionServices.jsonFile(Path)` for JSON-backed persisted direct assignments
- Current behavior:
  - stores permissions by `UUID` and normalized permission node
  - normalizes nodes with `trim().toLowerCase(Locale.ROOT)`
  - treats missing permissions as `UNSET`
  - `hasPermission(...)` returns `true` only for `PermissionValue.TRUE`
  - `getPermissions(...)` returns an immutable snapshot of explicit assignments for a subject
  - persists direct `UUID -> node -> TRUE/FALSE` assignments to `permissions.json`
  - treats `UNSET` as entry removal
  - fails startup on malformed persisted permission data
- Not implemented yet:
  - groups
  - inheritance
  - contexts
  - permission mutation commands
  - offline storage
  - permission attachment bridges
  - cross-platform synchronization

## Build And Tooling Rules
- Use the Gradle wrapper: `./gradlew`
- The wrapper is intentionally `9.4.1`
  - This is required by the current Fabric 26.1.2 + Loom 1.16.x setup.
  - Do not downgrade the wrapper to `8.x` unless the Fabric toolchain is changed too.
- Project code targets Java 25 through Gradle toolchains.
- Root project coordinates:
  - group: `me.clutchy.clutchperms`
  - version: `0.1.0-SNAPSHOT`
- Artifact naming is standardized at the root:
  - `clutchperms-common`
  - `clutchperms-paper`
  - `clutchperms-fabric`
  - `clutchperms-neoforge`
  - `clutchperms-forge`
- Copies of distributable runtime jars are collected in the root `build` directory.

## Version Matrix And Known Constraints
- Paper compile target:
  - `io.papermc.paper:paper-api:26.1.2.build.19-alpha`
- Fabric target:
  - Minecraft `26.1.2`
  - Fabric Loader `0.19.2`
  - Fabric API `0.146.1+26.1.2`
  - Loom `1.16-SNAPSHOT` resolving to `1.16.1`
- NeoForge target:
  - Minecraft `26.1.2`
  - NeoForge `26.1.2.22-beta`
  - ModDevGradle `2.0.141`
- Forge target:
  - Minecraft `26.1.2`
  - Forge `64.0.5`
  - ForgeGradle `7.0.25`
- Gson:
  - `2.13.2`
- MockBukkit tests:
  - `org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.108.0`
  - test runtime Paper API pinned to `1.21.11-R0.1-SNAPSHOT`
- Important:
  - MockBukkit does not currently align with the Paper `26.1.2` alpha API line.
  - The `paper` module compiles against the newer Paper API but tests against the MockBukkit-compatible Paper line.
  - Paper-only APIs are allowed in production code, but tests may need thin adapters or compile/build coverage when MockBukkit lags behind the current Paper API.

## Commands
- Full verification:
  - `./gradlew clean build`
- Standard test run:
  - `./gradlew check`
- Formatter check:
  - `./gradlew spotlessCheck`
- Auto-format sources and supported config files:
  - `./gradlew spotlessApply`
- Common module only:
  - `./gradlew :common:test`
- Paper tests only:
  - `./gradlew :paper:test`
- Fabric build only:
  - `./gradlew :fabric:build`
- NeoForge build only:
  - `./gradlew :neoforge:build`
- Forge build only:
  - `./gradlew :forge:build`
- Dependency inspection:
  - `./gradlew :paper:dependencies --configuration testRuntimeClasspath`

## Testing Expectations
- Before committing code changes, run the smallest relevant verification plus a repo-level check when the change crosses modules.
- Minimum expectations by change type:
  - shared API or shared behavior changes:
    - `./gradlew :common:test :paper:test`
  - Paper plugin changes:
    - `./gradlew :paper:test`
  - Fabric-only changes:
    - `./gradlew :fabric:build`
  - NeoForge-only changes:
    - `./gradlew :neoforge:build`
  - Forge-only changes:
    - `./gradlew :forge:build`
  - build logic or dependency changes:
    - `./gradlew clean build`
- Current test coverage:
  - `common`
    - unit tests for unset, true, false, and clear behavior
    - unit tests for permission enumeration and JSON persistence
  - `paper`
    - MockBukkit tests for plugin boot, service registration, and command response
  - `fabric`
    - no runtime tests yet
  - `neoforge`
    - no runtime tests yet
  - `forge`
    - no runtime tests yet

## Coding Guidelines
- Keep shared logic in `common` whenever it is not inherently platform-specific.
- Avoid introducing Bukkit or Fabric types into `common`.
- Preserve the current public surface in `common` unless the change clearly requires API expansion.
- Prefer small additive changes over premature abstractions.
- The Paper module is Paper-only; do not preserve Spigot compatibility unless explicitly requested.
- Keep Paper-only behavior isolated in thin platform adapters where practical so shared behavior remains testable in `common`.
- For Fabric, keep the initial scope server-side unless client behavior is intentionally introduced.
- For NeoForge, keep the initial scope server-side unless client behavior is intentionally introduced.
- For Forge, keep the initial scope server-side unless client behavior is intentionally introduced.

## Formatting
- Formatting is enforced with Spotless through the Gradle wrapper.
- Use `./gradlew spotlessApply` after editing Java sources, Gradle Kotlin DSL files, or supported repo config files.
- Use `./gradlew spotlessCheck` in CI-style validation or before committing formatting-sensitive changes.
- The project standard is a 180 character line length.
- Java formatting is handled by Spotless with the Eclipse JDT formatter so the 180 character limit is configurable.
- The shared Eclipse formatter profile lives at `eclipse-java-formatter.xml`.
- If Java formatting rules change, update both the Eclipse formatter profile and any related Spotless wiring in `build.gradle.kts`.
- `*.gradle.kts` files are formatted with Spotless + ktlint using the same 180 character line length.
- The Eclipse formatter profile is configured to join comment lines where possible, so manually wrapped short comments may be condensed by `spotlessApply`.
- Keep `.editorconfig` in sync with the Spotless rules when formatting conventions change.

## Javadocs And Comments
- Maintain Javadocs on public types and public or protected methods.
- When adding new Java classes, include a type-level Javadoc that explains the role of the class in the scaffold or platform lifecycle.
- When changing method behavior, update the method Javadoc in the same edit so the docs stay accurate.
- Keep short explanatory comments around non-obvious lifecycle, packaging, normalization, or test setup behavior.
- Do not add filler comments that only restate the code line-by-line.
- Preserve useful existing comments during refactors unless the behavior they describe has actually changed.
- If a refactor removes a public API or changes semantics, update any affected Javadocs, package docs, tests, `README.md`, and `AGENTS.md` together.

## Packaging Rules
- `paper`
  - must continue to ship with `common` classes inside the final plugin jar
  - must keep `plugin.yml` accurate when commands, permissions, or main class names change
- `fabric`
  - must continue to package `common` as a nested included jar
  - must keep `fabric.mod.json` version expansion wired through `processResources`
- `neoforge`
  - must continue to package `common` as a nested jar through NeoForge jar-in-jar metadata
  - must keep `neoforge.mods.toml` version and dependency expansion wired through `processResources`
- `forge`
  - must continue to ship with `common` classes inside the final mod jar
  - must keep `mods.toml` version and dependency expansion wired through `processResources`
- If a refactor changes artifact names or packaging behavior, update both the docs and the verification commands.
- Keep copies of the distributable Paper, Fabric, NeoForge, and Forge runtime jars landing in the shared root `build` folder unless there is a deliberate packaging change.

## Editing Guidance For Future Agents
- Read the relevant module build file before changing dependencies.
- Be careful with version bumps:
  - Paper API bumps may break MockBukkit tests even if main code still compiles.
  - Loom or Fabric bumps may require a Gradle wrapper change.
  - NeoForge or ModDevGradle bumps may require metadata, Java toolchain, or Gradle wrapper changes.
  - Forge or ForgeGradle bumps may require metadata, Java toolchain, or Gradle wrapper changes.
- If tests fail in `paper` during MockBukkit bootstrap, check the Paper API line used in test scope before changing production code.
- If a Paper-only API is not covered by MockBukkit yet, prefer a small adapter plus shared tests over moving platform types into `common`.
- If you add commands or permissions on Paper, update both:
  - `plugin.yml`
  - MockBukkit tests
- If you add new shared services or stateful behavior, prefer constructor-driven code in `common` and thin platform adapters in `paper`, `fabric`, `neoforge`, and `forge`.
- If you change persisted permission behavior or file locations, update both `README.md` and this file.
- If you change NeoForge entrypoints or dependencies, update `neoforge.mods.toml`.
- If you change Forge entrypoints or dependencies, update `mods.toml`.

## Commit Rules
- Use Conventional Commits: `type(scope): summary`
- Keep the summary lowercase.
- Prefer scopes when they clarify the area changed, such as:
  - `build`
  - `common`
  - `paper`
  - `fabric`
  - `docs`
  - `tooling`
- Examples:
  - `feat(common): add permission node validator`
  - `fix(paper): register service on plugin enable`
  - `docs(readme): explain mockbukkit version constraint`
- Use a single `-m` unless a short list body adds real value.
- Do not amend commits unless explicitly asked.

## Documentation Maintenance
- Keep `README.md` aligned with reality.
- Keep Javadocs and inline comments aligned with reality.
- Update `AGENTS.md` when any of these change:
  - module responsibilities
  - required build commands
  - version compatibility caveats
  - packaging behavior
  - testing expectations
