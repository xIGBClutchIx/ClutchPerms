# Storage And Config Reference

ClutchPerms writes one config file and one SQLite database:

| File | Purpose |
| --- | --- |
| `config.json` | Runtime settings for backup retention/scheduling, audit retention, command page sizes, chat formatting, and Paper command replacement |
| `database.db` | Direct permissions, group definitions, track definitions, memberships, subject metadata, display values, manually registered known nodes, and command audit history |

Locations:

| Platform | Location |
| --- | --- |
| Paper | plugin data folder |
| Fabric | Fabric config dir, `clutchperms/` |
| NeoForge | NeoForge config dir, `clutchperms/` |
| Forge | Forge config dir, `clutchperms/` |

Missing database storage loads as empty state with the built-in `default` and `op` groups. Missing `config.json` loads defaults. Missing files are materialized after successful startup or reload.

## Default Config

```json
{
  "version": 1,
  "backups": {
    "retentionLimit": 10,
    "schedule": {
      "enabled": false,
      "intervalMinutes": 60,
      "runOnStartup": false
    }
  },
  "audit": {
    "retention": {
      "enabled": true,
      "maxAgeDays": 90,
      "maxEntries": 0
    }
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

## Config Keys

| Key | Default | Range | Description |
| --- | --- | --- | --- |
| `backups.retentionLimit` | `10` | `1..1000` | Newest database backups kept. |
| `backups.schedule.enabled` | `false` | boolean | Enables automatic database backups. |
| `backups.schedule.intervalMinutes` | `60` | `5..10080` | Automatic backup interval. |
| `backups.schedule.runOnStartup` | `false` | boolean | Creates one scheduled backup after successful startup load. |
| `audit.retention.enabled` | `true` | boolean | Enables automatic audit history retention pruning. |
| `audit.retention.maxAgeDays` | `90` | `1..3650` | Maximum audit row age in days. |
| `audit.retention.maxEntries` | `0` | `0..1000000` | Newest audit rows kept. `0` disables count retention. |
| `commands.helpPageSize` | `7` | configured command range | Command rows shown per help page. |
| `commands.resultPageSize` | `8` | configured command range | Rows shown per list-result page. |
| `chat.enabled` | `true` | boolean | Enables prefix/suffix chat formatting. |
| `paper.replaceOpCommands` | `true` | boolean | Registers ClutchPerms `/op` and `/deop` replacements on Paper. |

Config changes apply immediately through save-and-reload. If reload fails, `config.json` is rolled back.

## Validation

Validation is strict. Startup, validate, or reload fails on malformed config, unsupported schema versions, unknown config keys, invalid config values, invalid UUIDs, blank names or nodes, duplicate normalized permission keys, invalid wildcard placement, unknown permission values, unknown groups, explicit `default` memberships, invalid protected `op` definitions, unknown parent groups, parent cycles, unknown track groups, duplicate track groups, non-contiguous stored track positions, `default` outside track position 1, or any track use of `op`.

Stored display values are also validated. Raw section signs, invalid ampersand codes, blank display values, and over-length display values fail startup, validate, or reload.

`/clutchperms validate` parses config and database storage without replacing active services/config, refreshing runtime bridges, or mutating storage.

## Tracks

Tracks are stored in the same SQLite database through `tracks` and ordered `track_groups` rows. They define ordered group ladders for shared promote and demote commands, but they do not change permission or display resolution.

Track names and track group names are normalized before storage. `default` is allowed only as the first track entry. `op` is rejected for tracks entirely.

When a referenced group is renamed, track rows are updated in the same transaction. When a referenced group is deleted, related track rows are removed and their remaining positions are compacted so stored track ordering stays contiguous.

User track promote and demote operations replace explicit group memberships in one transaction. ClutchPerms never commits the “remove old group” half of a track move without the matching “add new group” half.

## Display And Chat

Display values are stored in the database as ampersand-formatted strings. Direct user display values are keyed by UUID, and group display values are keyed by normalized group name.

Display text accepts `&0-9`, `&a-f`, `&k-o`, `&r`, and `&&`. Raw section signs, blank values, invalid codes, and values over 128 raw characters are rejected.

Effective prefix and suffix are resolved independently: direct user value first, then the nearest explicit group hierarchy, then the nearest `default` group hierarchy. If multiple groups at the same depth provide a value, the alphabetically first group name wins.

Chat display is active by default on all supported platforms, can be toggled with `chat.enabled`, and renders as `prefix name suffix: message`. Because ClutchPerms formats the full chat line, some loaders or clients may treat the line as modified or unsigned.

## Backups

Backups are whole-database snapshots created manually with `/clutchperms backup create` or automatically when `backups.schedule.enabled` is true. Scheduled backups are disabled by default, run every 60 minutes when enabled, can optionally run once on startup, and use the same retention pruning as manual backups.

Backup layout:

```text
backups/
  database/database-YYYYMMDD-HHMMSSSSS.db
```

Backup restore validates the selected backup file before replacing live storage. ClutchPerms closes the active SQLite pool, replaces `database.db`, removes stale WAL/SHM sidecar files, reloads config and database storage, and rolls the restored database back if reload fails.

`config.json` is not included in backup restore.

## Audit History

Successful command-layer mutations are written to the SQLite audit log with actor, target, action, timestamp, before/after snapshots, source command, and undo state.

Audit history retention is enabled by default. ClutchPerms keeps audit rows for 90 days, with no count cap unless `audit.retention.maxEntries` is set above `0`. Automatic retention runs after runtime load/reload and after successful audited command mutations.

Manual `/clutchperms history prune days <days>` and `/clutchperms history prune count <count>` commands require repeat confirmation and may remove undoable entries. Manual prune writes a non-undoable audit row before pruning.

`/clutchperms undo <id>` refuses to overwrite newer changes: the current target state must still match the audited after snapshot. Undo writes its own non-undoable audit entry and marks the original entry undone.

Backup create/restore, manual known-node changes, player-observation metadata updates, automatic audit retention, and internal service calls outside commands are not audited in this version.

## Mutation Guarantees

If a database mutation cannot commit, ClutchPerms leaves both the live database and the in-memory runtime state unchanged. This includes track edits and track-driven membership replacements.

Reload is atomic from the command perspective. If config or database storage fails, active runtime state remains unchanged.
