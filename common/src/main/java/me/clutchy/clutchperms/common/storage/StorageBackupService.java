package me.clutchy.clutchperms.common.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import me.clutchy.clutchperms.common.config.ClutchPermsBackupConfig;

/**
 * Creates, lists, prunes, and restores rolling SQLite database backups.
 */
public final class StorageBackupService {

    /**
     * Number of newest backups retained.
     */
    public static final int RETENTION_LIMIT = 10;

    private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS").withZone(ZoneOffset.UTC);

    private static final String BACKUP_EXTENSION = ".db";

    private final Path backupRoot;

    private final Path databaseFile;

    private final SqliteStore sqliteStore;

    private final Clock clock;

    private final int retentionLimit;

    /**
     * Creates a backup service for the active SQLite database.
     *
     * @param backupRoot root directory that contains database backups
     * @param databaseFile live database file
     * @param sqliteStore active SQLite store used for consistent snapshots
     * @param retentionLimit newest backups retained
     * @return backup service
     */
    public static StorageBackupService forDatabase(Path backupRoot, Path databaseFile, SqliteStore sqliteStore, int retentionLimit) {
        return new StorageBackupService(backupRoot, databaseFile, sqliteStore, Clock.systemUTC(), retentionLimit);
    }

    StorageBackupService(Path backupRoot, Path databaseFile, SqliteStore sqliteStore, Clock clock) {
        this(backupRoot, databaseFile, sqliteStore, clock, RETENTION_LIMIT);
    }

    StorageBackupService(Path backupRoot, Path databaseFile, SqliteStore sqliteStore, Clock clock, int retentionLimit) {
        this.backupRoot = Objects.requireNonNull(backupRoot, "backupRoot").toAbsolutePath().normalize();
        this.databaseFile = Objects.requireNonNull(databaseFile, "databaseFile").toAbsolutePath().normalize();
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.retentionLimit = new ClutchPermsBackupConfig(retentionLimit).retentionLimit();
    }

    /**
     * Returns the only storage kind backed up by this service.
     *
     * @return database storage kind
     */
    public List<StorageFileKind> fileKinds() {
        return List.of(StorageFileKind.DATABASE);
    }

    /**
     * Returns newest-backup retention for this service.
     *
     * @return newest backups retained
     */
    public int retentionLimit() {
        return retentionLimit;
    }

    /**
     * Lists database backups, newest first.
     *
     * @return backups grouped by database kind
     */
    public Map<StorageFileKind, List<StorageBackup>> listBackups() {
        EnumMap<StorageFileKind, List<StorageBackup>> backups = new EnumMap<>(StorageFileKind.class);
        backups.put(StorageFileKind.DATABASE, listBackups(StorageFileKind.DATABASE));
        return Collections.unmodifiableMap(backups);
    }

    /**
     * Lists database backups, newest first.
     *
     * @param kind storage kind; must be {@link StorageFileKind#DATABASE}
     * @return backup descriptors
     */
    public List<StorageBackup> listBackups(StorageFileKind kind) {
        requireDatabaseKind(kind);
        Path backupDirectory = backupDirectory();
        if (Files.notExists(backupDirectory)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.list(backupDirectory)) {
            return paths.filter(Files::isRegularFile).map(this::toBackup).filter(Optional::isPresent).map(Optional::orElseThrow).sorted(StorageBackupService::compareNewestFirst)
                    .toList();
        } catch (NoSuchFileException exception) {
            return List.of();
        } catch (IOException exception) {
            throw new PermissionStorageException("Failed to list database backups from " + backupDirectory, exception);
        }
    }

    /**
     * Creates a consistent SQLite database snapshot.
     *
     * @return created backup descriptor, or empty when the live database is missing
     */
    public Optional<StorageBackup> createBackup() {
        return backupExistingFile(StorageFileKind.DATABASE);
    }

    /**
     * Creates a consistent SQLite database snapshot.
     *
     * @param kind storage kind; must be {@link StorageFileKind#DATABASE}
     * @return created backup descriptor, or empty when the live database is missing
     */
    public Optional<StorageBackup> backupExistingFile(StorageFileKind kind) {
        requireDatabaseKind(kind);
        if (Files.notExists(databaseFile)) {
            return Optional.empty();
        }

        Path backupDirectory = backupDirectory();
        Path backupFile = null;
        try {
            Files.createDirectories(backupDirectory);
            backupFile = uniqueBackupPath(backupDirectory, clock.instant());
            Path snapshotFile = backupFile;
            sqliteStore.read(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("VACUUM INTO '" + sqlLiteral(snapshotFile.toString()) + "'");
                }
                return null;
            });
            pruneBackups();
            return Optional.of(new StorageBackup(StorageFileKind.DATABASE, backupFile.getFileName().toString(), backupFile));
        } catch (RuntimeException | IOException exception) {
            if (backupFile != null) {
                try {
                    Files.deleteIfExists(backupFile);
                } catch (IOException ignored) {
                    // Primary failure is reported below.
                }
            }
            throw new PermissionStorageException("Failed to create database backup for " + databaseFile, exception);
        }
    }

    /**
     * Restores one backup file and rolls the live file back if applying restored storage fails.
     *
     * @param kind storage kind; must be {@link StorageFileKind#DATABASE}
     * @param backupFileName backup filename from {@link #listBackups(StorageFileKind)}
     * @param applyRestoredStorage action that reloads and refreshes restored storage
     */
    public void restoreBackup(StorageFileKind kind, String backupFileName, Runnable applyRestoredStorage) {
        restoreBackup(kind, backupFileName, () -> {
        }, applyRestoredStorage);
    }

    /**
     * Restores one backup file and rolls the live file back if applying restored storage fails.
     *
     * @param kind storage kind; must be {@link StorageFileKind#DATABASE}
     * @param backupFileName backup filename from {@link #listBackups(StorageFileKind)}
     * @param beforeReplace action run after the restored copy is staged and before the live database is replaced
     * @param applyRestoredStorage action that reloads and refreshes restored storage
     */
    public void restoreBackup(StorageFileKind kind, String backupFileName, Runnable beforeReplace, Runnable applyRestoredStorage) {
        requireDatabaseKind(kind);
        Objects.requireNonNull(beforeReplace, "beforeReplace");
        Objects.requireNonNull(applyRestoredStorage, "applyRestoredStorage");
        Path backupFile = resolveBackupFile(backupFileName);
        Path parentDirectory = databaseFile.getParent();
        Path rollbackFile = null;
        Path restoreFile = null;
        boolean liveFileExisted = Files.exists(databaseFile);
        try {
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }
            if (liveFileExisted) {
                rollbackFile = parentDirectory == null
                        ? Files.createTempFile(databaseFile.getFileName().toString(), ".restore")
                        : Files.createTempFile(parentDirectory, databaseFile.getFileName().toString(), ".restore");
                Files.copy(databaseFile, rollbackFile, StandardCopyOption.REPLACE_EXISTING);
            }

            restoreFile = parentDirectory == null
                    ? Files.createTempFile(databaseFile.getFileName().toString(), ".restore")
                    : Files.createTempFile(parentDirectory, databaseFile.getFileName().toString(), ".restore");
            Files.copy(backupFile, restoreFile, StandardCopyOption.REPLACE_EXISTING);
            beforeReplace.run();
            deleteSidecars(databaseFile);
            StorageFiles.moveAtomically(restoreFile, databaseFile);
            restoreFile = null;
            try {
                applyRestoredStorage.run();
            } catch (RuntimeException exception) {
                rollbackLiveFile(rollbackFile, liveFileExisted, exception);
                throw new PermissionStorageException("Failed to apply restored database backup " + backupFile.getFileName(), exception);
            }
        } catch (IOException exception) {
            rollbackLiveFile(rollbackFile, liveFileExisted, exception);
            throw new PermissionStorageException("Failed to restore database backup " + backupFile.getFileName(), exception);
        } finally {
            if (restoreFile != null) {
                try {
                    Files.deleteIfExists(restoreFile);
                } catch (IOException ignored) {
                    // Best-effort cleanup; restore failures are reported above.
                }
            }
            if (rollbackFile != null) {
                try {
                    Files.deleteIfExists(rollbackFile);
                } catch (IOException ignored) {
                    // Best-effort cleanup; restore failures are reported above.
                }
            }
        }
    }

    private Path backupDirectory() {
        return backupRoot.resolve(StorageFileKind.DATABASE.token());
    }

    private void pruneBackups() throws IOException {
        List<StorageBackup> backups = new ArrayList<>(listBackups(StorageFileKind.DATABASE));
        for (int index = retentionLimit; index < backups.size(); index++) {
            Files.deleteIfExists(backups.get(index).path());
        }
    }

    private Path uniqueBackupPath(Path backupDirectory, Instant instant) {
        String baseName = StorageFileKind.DATABASE.token() + "-" + BACKUP_TIMESTAMP_FORMATTER.format(instant);
        Path backupFile = backupDirectory.resolve(baseName + BACKUP_EXTENSION);
        int counter = 2;
        while (Files.exists(backupFile)) {
            backupFile = backupDirectory.resolve(baseName + "-" + counter + BACKUP_EXTENSION);
            counter++;
        }
        return backupFile;
    }

    private Path resolveBackupFile(String backupFileName) {
        String requiredFileName = Objects.requireNonNull(backupFileName, "backupFileName");
        if (requiredFileName.isBlank() || !Path.of(requiredFileName).getFileName().toString().equals(requiredFileName)) {
            throw new IllegalArgumentException("invalid backup filename: " + requiredFileName);
        }

        Path backupFile = backupDirectory().resolve(requiredFileName).toAbsolutePath().normalize();
        if (!backupFile.startsWith(backupDirectory().toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("invalid backup filename: " + requiredFileName);
        }
        if (!Files.isRegularFile(backupFile)) {
            throw new IllegalArgumentException("unknown database backup: " + requiredFileName);
        }
        return backupFile;
    }

    private Optional<StorageBackup> toBackup(Path path) {
        String fileName = path.getFileName().toString();
        if (!fileName.startsWith(StorageFileKind.DATABASE.token() + "-") || !fileName.endsWith(BACKUP_EXTENSION)) {
            return Optional.empty();
        }
        return Optional.of(new StorageBackup(StorageFileKind.DATABASE, fileName, path));
    }

    private void rollbackLiveFile(Path rollbackFile, boolean liveFileExisted, Throwable restoreFailure) {
        try {
            deleteSidecars(databaseFile);
            if (liveFileExisted) {
                if (rollbackFile == null) {
                    return;
                }
                Files.copy(Objects.requireNonNull(rollbackFile, "rollbackFile"), databaseFile, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(databaseFile);
            }
        } catch (IOException rollbackFailure) {
            PermissionStorageException failure = new PermissionStorageException("Failed to roll back live database " + databaseFile + " after restore failure", restoreFailure);
            failure.addSuppressed(rollbackFailure);
            throw failure;
        }
    }

    private static int compareNewestFirst(StorageBackup first, StorageBackup second) {
        BackupFileOrder firstOrder = BackupFileOrder.from(first.fileName());
        BackupFileOrder secondOrder = BackupFileOrder.from(second.fileName());
        int timestampOrder = secondOrder.timestamp().compareTo(firstOrder.timestamp());
        if (timestampOrder != 0) {
            return timestampOrder;
        }
        int counterOrder = Integer.compare(secondOrder.counter(), firstOrder.counter());
        if (counterOrder != 0) {
            return counterOrder;
        }
        return second.fileName().compareTo(first.fileName());
    }

    private static void requireDatabaseKind(StorageFileKind kind) {
        if (Objects.requireNonNull(kind, "kind") != StorageFileKind.DATABASE) {
            throw new IllegalArgumentException("storage kind is not backed up by SQLite storage: " + kind.token());
        }
    }

    private static void deleteSidecars(Path databaseFile) throws IOException {
        Files.deleteIfExists(Path.of(databaseFile.toString() + "-wal"));
        Files.deleteIfExists(Path.of(databaseFile.toString() + "-shm"));
    }

    private static String sqlLiteral(String value) {
        return value.replace("'", "''");
    }

    private record BackupFileOrder(String timestamp, int counter) {

        private static BackupFileOrder from(String fileName) {
            String withoutExtension = fileName.substring(0, fileName.length() - BACKUP_EXTENSION.length());
            int firstDash = withoutExtension.indexOf('-');
            if (firstDash < 0 || firstDash + 1 >= withoutExtension.length()) {
                return new BackupFileOrder(withoutExtension, 1);
            }

            String timestampAndCounter = withoutExtension.substring(firstDash + 1);
            int counterSeparator = timestampAndCounter.lastIndexOf('-');
            if (counterSeparator < 0) {
                return new BackupFileOrder(timestampAndCounter, 1);
            }

            String timestamp = timestampAndCounter.substring(0, counterSeparator);
            String counterText = timestampAndCounter.substring(counterSeparator + 1);
            try {
                return new BackupFileOrder(timestamp, Integer.parseInt(counterText));
            } catch (NumberFormatException exception) {
                return new BackupFileOrder(timestampAndCounter, 1);
            }
        }
    }
}
