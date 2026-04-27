package me.clutchy.clutchperms.common.runtime;

import java.time.Instant;
import java.util.Optional;

/**
 * Point-in-time scheduled backup runner status.
 *
 * @param enabled whether automatic backups are enabled
 * @param intervalMinutes configured interval in minutes
 * @param runOnStartup configured startup backup state
 * @param running whether the scheduler currently has an active timer
 * @param nextRun next scheduled run time, if any
 * @param lastSuccess last successful scheduled or run-now backup time, if any
 * @param lastBackupFile last backup filename created by the scheduler, if any
 * @param lastFailure last failed scheduled or run-now backup time, if any
 * @param lastFailureMessage last failure message, if any
 */
public record ScheduledBackupStatus(boolean enabled, int intervalMinutes, boolean runOnStartup, boolean running, Optional<Instant> nextRun, Optional<Instant> lastSuccess,
        Optional<String> lastBackupFile, Optional<Instant> lastFailure, Optional<String> lastFailureMessage) {
}
