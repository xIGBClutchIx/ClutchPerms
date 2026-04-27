package me.clutchy.clutchperms.common.runtime;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.clutchy.clutchperms.common.config.ClutchPermsBackupConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsBackupScheduleConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsCommandConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.storage.SqliteDependencyMode;
import me.clutchy.clutchperms.common.storage.SqliteStore;
import me.clutchy.clutchperms.common.storage.StorageBackup;
import me.clutchy.clutchperms.common.storage.StorageBackupService;
import me.clutchy.clutchperms.common.storage.StorageFileKind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies scheduled backup runner state transitions and backup creation behavior.
 */
class ScheduledBackupServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-27T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    private Path temporaryDirectory;

    @Test
    void disabledSchedulesDoNotQueueBackups() {
        ManualScheduledExecutor executor = new ManualScheduledExecutor();
        AtomicReference<ClutchPermsConfig> config = new AtomicReference<>(config(false, 60, false));
        ScheduledBackupService scheduler = scheduler(config, executor, storageBackupService());

        scheduler.start();

        ScheduledBackupStatus status = scheduler.status();
        assertFalse(status.enabled());
        assertFalse(status.running());
        assertEquals(Optional.empty(), status.nextRun());
        assertEquals(0, executor.scheduledTasks());
    }

    @Test
    void enabledSchedulesQueueAndRunBackups() {
        ManualScheduledExecutor executor = new ManualScheduledExecutor();
        AtomicReference<ClutchPermsConfig> config = new AtomicReference<>(config(true, 60, false));
        StorageBackupService backupService = storageBackupService();
        ScheduledBackupService scheduler = scheduler(config, executor, backupService);

        scheduler.start();
        assertEquals(1, executor.scheduledTasks());
        assertEquals(60, executor.lastDelayMinutes());

        executor.runScheduled();

        List<StorageBackup> backups = backupService.listBackups(StorageFileKind.DATABASE);
        assertEquals(1, backups.size());
        assertTrue(scheduler.status().lastSuccess().isPresent());
        assertEquals(Optional.of(backups.getFirst().fileName()), scheduler.status().lastBackupFile());
        assertEquals(2, executor.scheduledTasks());
    }

    @Test
    void runOnStartupRunsImmediatelyThenSchedulesNextInterval() {
        ManualScheduledExecutor executor = new ManualScheduledExecutor();
        AtomicReference<ClutchPermsConfig> config = new AtomicReference<>(config(true, 30, true));
        StorageBackupService backupService = storageBackupService();
        ScheduledBackupService scheduler = scheduler(config, executor, backupService);

        scheduler.start();

        assertEquals(1, backupService.listBackups(StorageFileKind.DATABASE).size());
        assertEquals(1, executor.scheduledTasks());
        assertEquals(30, executor.lastDelayMinutes());
    }

    @Test
    void restartAppliesUpdatedIntervalAndEnabledState() {
        ManualScheduledExecutor executor = new ManualScheduledExecutor();
        AtomicReference<ClutchPermsConfig> config = new AtomicReference<>(config(true, 60, false));
        ScheduledBackupService scheduler = scheduler(config, executor, storageBackupService());

        scheduler.start();
        config.set(config(true, 5, false));
        scheduler.restart();

        assertEquals(5, executor.lastDelayMinutes());

        config.set(config(false, 5, false));
        scheduler.restart();

        assertFalse(scheduler.status().running());
    }

    @Test
    void failuresAreRecordedAndFutureRunsContinue() {
        ManualScheduledExecutor executor = new ManualScheduledExecutor();
        AtomicReference<ClutchPermsConfig> config = new AtomicReference<>(config(true, 60, false));
        SqliteStore store = SqliteStore.open(temporaryDirectory.resolve("database.db"), SqliteDependencyMode.ANY_VISIBLE);
        StorageBackupService backupService = StorageBackupService.forDatabase(temporaryDirectory.resolve("backups"), store.databaseFile(), store, 10);
        ScheduledBackupService scheduler = scheduler(config, executor, backupService);
        store.close();

        scheduler.start();
        executor.runScheduled();

        assertTrue(scheduler.status().lastFailure().isPresent());
        assertTrue(scheduler.status().lastFailureMessage().isPresent());
        assertEquals(2, executor.scheduledTasks());
    }

    private ScheduledBackupService scheduler(AtomicReference<ClutchPermsConfig> config, ManualScheduledExecutor executor, StorageBackupService backupService) {
        return new ScheduledBackupService(config::get, () -> backupService, ignored -> {
        }, (ignored, exception) -> {
        }, executor, CLOCK);
    }

    private StorageBackupService storageBackupService() {
        SqliteStore store = SqliteStore.open(temporaryDirectory.resolve("database.db"), SqliteDependencyMode.ANY_VISIBLE);
        return StorageBackupService.forDatabase(temporaryDirectory.resolve("backups"), store.databaseFile(), store, 10);
    }

    private static ClutchPermsConfig config(boolean enabled, int intervalMinutes, boolean runOnStartup) {
        return new ClutchPermsConfig(new ClutchPermsBackupConfig(10, new ClutchPermsBackupScheduleConfig(enabled, intervalMinutes, runOnStartup)),
                ClutchPermsCommandConfig.defaults());
    }

    private static final class ManualScheduledExecutor extends AbstractExecutorService implements ScheduledExecutorService {

        private Runnable scheduled;

        private long lastDelayMinutes;

        private int scheduledTasks;

        private boolean shutdown;

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            scheduled = command;
            lastDelayMinutes = unit.toMinutes(delay);
            scheduledTasks++;
            return new ManualScheduledFuture();
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        void runScheduled() {
            scheduled.run();
        }

        long lastDelayMinutes() {
            return lastDelayMinutes;
        }

        int scheduledTasks() {
            return scheduledTasks;
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public <V> ScheduledFuture<V> schedule(java.util.concurrent.Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class ManualScheduledFuture implements ScheduledFuture<Object> {

        private boolean cancelled;

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
