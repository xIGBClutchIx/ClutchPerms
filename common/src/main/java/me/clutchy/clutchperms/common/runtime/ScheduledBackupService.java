package me.clutchy.clutchperms.common.runtime;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import me.clutchy.clutchperms.common.config.ClutchPermsBackupScheduleConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.storage.StorageBackup;
import me.clutchy.clutchperms.common.storage.StorageBackupService;

/**
 * Runs automatic database backups using the active runtime backup service.
 */
public final class ScheduledBackupService implements AutoCloseable {

    private final Supplier<ClutchPermsConfig> configSupplier;

    private final Supplier<StorageBackupService> backupServiceSupplier;

    private final java.util.function.Consumer<String> infoLogger;

    private final BiConsumer<String, Throwable> errorLogger;

    private final ScheduledExecutorService executor;

    private final Clock clock;

    private ScheduledFuture<?> future;

    private boolean closed;

    private boolean enabled;

    private int intervalMinutes = ClutchPermsBackupScheduleConfig.DEFAULT_INTERVAL_MINUTES;

    private boolean runOnStartup;

    private Instant nextRun;

    private Instant lastSuccess;

    private String lastBackupFile;

    private Instant lastFailure;

    private String lastFailureMessage;

    /**
     * Creates a scheduled backup runner.
     *
     * @param configSupplier active config supplier
     * @param backupServiceSupplier active backup service supplier
     * @param infoLogger informational logger
     * @param errorLogger error logger
     */
    public ScheduledBackupService(Supplier<ClutchPermsConfig> configSupplier, Supplier<StorageBackupService> backupServiceSupplier, java.util.function.Consumer<String> infoLogger,
            BiConsumer<String, Throwable> errorLogger) {
        this(configSupplier, backupServiceSupplier, infoLogger, errorLogger, Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "ClutchPerms scheduled backups");
            thread.setDaemon(true);
            return thread;
        }), Clock.systemUTC());
    }

    ScheduledBackupService(Supplier<ClutchPermsConfig> configSupplier, Supplier<StorageBackupService> backupServiceSupplier, java.util.function.Consumer<String> infoLogger,
            BiConsumer<String, Throwable> errorLogger, ScheduledExecutorService executor, Clock clock) {
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.backupServiceSupplier = Objects.requireNonNull(backupServiceSupplier, "backupServiceSupplier");
        this.infoLogger = Objects.requireNonNull(infoLogger, "infoLogger");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Starts the scheduler from active config.
     */
    public synchronized void start() {
        start(true);
    }

    /**
     * Restarts the scheduler after config reload.
     */
    public synchronized void restart() {
        start(false);
    }

    /**
     * Stops any active scheduled future.
     */
    public synchronized void stop() {
        cancelFuture();
        nextRun = null;
        enabled = false;
    }

    /**
     * Creates a backup immediately, regardless of whether automatic backups are enabled.
     *
     * @return created backup, or empty when the live database is missing
     */
    public Optional<StorageBackup> runNow() {
        try {
            Optional<StorageBackup> backup = backupServiceSupplier.get().createBackup();
            synchronized (this) {
                lastSuccess = clock.instant();
                lastBackupFile = backup.map(StorageBackup::fileName).orElse(null);
                lastFailure = null;
                lastFailureMessage = null;
            }
            backup.ifPresent(created -> infoLogger.accept("Created scheduled ClutchPerms database backup " + created.fileName() + "."));
            return backup;
        } catch (RuntimeException exception) {
            synchronized (this) {
                lastFailure = clock.instant();
                lastFailureMessage = exception.getMessage();
            }
            errorLogger.accept("Failed to create scheduled ClutchPerms database backup.", exception);
            throw exception;
        }
    }

    /**
     * Returns current scheduler status.
     *
     * @return scheduler status
     */
    public synchronized ScheduledBackupStatus status() {
        return new ScheduledBackupStatus(enabled, intervalMinutes, runOnStartup, future != null && !future.isCancelled(), Optional.ofNullable(nextRun),
                Optional.ofNullable(lastSuccess), Optional.ofNullable(lastBackupFile), Optional.ofNullable(lastFailure), Optional.ofNullable(lastFailureMessage));
    }

    @Override
    public synchronized void close() {
        stop();
        closed = true;
        executor.shutdownNow();
    }

    private void start(boolean startup) {
        cancelFuture();
        ClutchPermsBackupScheduleConfig schedule = configSupplier.get().backups().schedule();
        enabled = schedule.enabled();
        intervalMinutes = schedule.intervalMinutes();
        runOnStartup = schedule.runOnStartup();
        if (closed || !enabled) {
            nextRun = null;
            return;
        }
        if (startup && runOnStartup) {
            executor.execute(this::runScheduledBackupAndReschedule);
            return;
        }
        scheduleNext(intervalMinutes);
    }

    private void runScheduledBackupAndReschedule() {
        try {
            runNow();
        } catch (RuntimeException ignored) {
            // Failure has already been recorded and logged; keep future scheduled runs alive.
        } finally {
            synchronized (this) {
                if (!closed && enabled) {
                    scheduleNext(intervalMinutes);
                }
            }
        }
    }

    private void scheduleNext(int delayMinutes) {
        nextRun = clock.instant().plusSeconds(TimeUnit.MINUTES.toSeconds(delayMinutes));
        future = executor.schedule(this::runScheduledBackupAndReschedule, delayMinutes, TimeUnit.MINUTES);
    }

    private void cancelFuture() {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }
}
