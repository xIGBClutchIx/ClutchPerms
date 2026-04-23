# AGENTS.md

## Purpose
- `ClutchPerms` is a multi-framework Java project intended to grow into a shared permission system that works across Paper/Spigot and Fabric.
- The current state is a scaffold, not a finished permission platform.
- The repo currently provides:
  - a shared `common` module with a minimal permission API and in-memory implementation
  - a `paper` plugin module with a diagnostic `/clutchperms` command and Bukkit service registration
  - a `fabric` mod module with a diagnostic `/clutchperms` command and the same shared service

## Repository Layout
- `common`
  - pure Java shared code
  - no Bukkit, Paper, Fabric, or Minecraft dependencies
  - owns the public permission model for now
- `paper`
  - Bukkit-safe server plugin
  - compiles against Paper API
  - embeds `common` classes directly into the produced jar
  - uses `plugin.yml`, not `paper-plugin.yml`
- `fabric`
  - Fabric mod built with Loom
  - bundles `common` as a nested jar via `include(project(":common"))`
- root build
  - Gradle Kotlin DSL multi-project setup
  - centralized Java toolchain and test configuration

## Current Functional Scope
- Shared public API:
  - `PermissionService`
  - `PermissionValue`
  - `PermissionNodes`
- Current backend:
  - `InMemoryPermissionService`
- Current behavior:
  - stores permissions by `UUID` and normalized permission node
  - normalizes nodes with `trim().toLowerCase(Locale.ROOT)`
  - treats missing permissions as `UNSET`
  - `hasPermission(...)` returns `true` only for `PermissionValue.TRUE`
- Not implemented yet:
  - persistence
  - groups
  - inheritance
  - contexts
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
- Copies of distributable runtime jars are collected in the root `build` directory.

## Version Matrix And Known Constraints
- Paper compile target:
  - `io.papermc.paper:paper-api:26.1.2.build.19-alpha`
- Fabric target:
  - Minecraft `26.1.2`
  - Fabric Loader `0.19.2`
  - Fabric API `0.146.1+26.1.2`
  - Loom `1.16-SNAPSHOT` resolving to `1.16.1`
- MockBukkit tests:
  - `org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.108.0`
  - test runtime Paper API pinned to `1.21.11-R0.1-SNAPSHOT`
- Important:
  - MockBukkit does not currently align with the Paper `26.1.2` alpha API line.
  - The `paper` module compiles against the newer Paper API but tests against the MockBukkit-compatible Paper line.
  - Keep the Paper module Bukkit-safe so this mismatch stays manageable.

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
  - build logic or dependency changes:
    - `./gradlew clean build`
- Current test coverage:
  - `common`
    - unit tests for unset, true, false, and clear behavior
  - `paper`
    - MockBukkit tests for plugin boot, service registration, and command response
  - `fabric`
    - no runtime tests yet

## Coding Guidelines
- Keep shared logic in `common` whenever it is not inherently platform-specific.
- Avoid introducing Bukkit or Fabric types into `common`.
- Preserve the current public surface in `common` unless the change clearly requires API expansion.
- Prefer small additive changes over premature abstractions.
- Keep the Paper module Bukkit-safe unless there is an explicit decision to use Paper-only APIs.
- If you need Paper-only behavior later, isolate it clearly so Spigot compatibility is not accidentally broken.
- For Fabric, keep the initial scope server-side unless client behavior is intentionally introduced.

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
- If a refactor changes artifact names or packaging behavior, update both the docs and the verification commands.
- Keep copies of the distributable Paper and Fabric runtime jars landing in the shared root `build` folder unless there is a deliberate packaging change.

## Editing Guidance For Future Agents
- Read the relevant module build file before changing dependencies.
- Be careful with version bumps:
  - Paper API bumps may break MockBukkit tests even if main code still compiles.
  - Loom or Fabric bumps may require a Gradle wrapper change.
- If tests fail in `paper` during MockBukkit bootstrap, check the Paper API line used in test scope before changing production code.
- If you add commands or permissions on Paper, update both:
  - `plugin.yml`
  - MockBukkit tests
- If you add new shared services or stateful behavior, prefer constructor-driven code in `common` and thin platform adapters in `paper` and `fabric`.

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
