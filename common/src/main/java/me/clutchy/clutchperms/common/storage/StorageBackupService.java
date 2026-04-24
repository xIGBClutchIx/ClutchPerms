package me.clutchy.clutchperms.common.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Creates, lists, prunes, and restores rolling per-file backups for ClutchPerms JSON storage.
 */
public final class StorageBackupService {

    /**
     * Number of newest backups retained per storage file.
     */
    public static final int RETENTION_LIMIT = 10;

    private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS").withZone(ZoneOffset.UTC);

    private final Path backupRoot;

    private final Map<StorageFileKind, Path> liveFiles;

    private final Clock clock;

    /**
     * Creates a backup service for a set of live storage files.
     *
     * @param backupRoot root directory that contains per-kind backup directories
     * @param liveFiles live storage files by kind
     * @return backup service
     */
    public static StorageBackupService forFiles(Path backupRoot, Map<StorageFileKind, Path> liveFiles) {
        return new StorageBackupService(backupRoot, liveFiles, Clock.systemUTC());
    }

    StorageBackupService(Path backupRoot, Map<StorageFileKind, Path> liveFiles, Clock clock) {
        this.backupRoot = Objects.requireNonNull(backupRoot, "backupRoot").toAbsolutePath().normalize();
        this.liveFiles = normalizeLiveFiles(liveFiles);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Returns all known live file kinds for this backup service.
     *
     * @return storage file kinds
     */
    public List<StorageFileKind> fileKinds() {
        return List.copyOf(liveFiles.keySet());
    }

    /**
     * Lists backups for every live file kind, newest first within each kind.
     *
     * @return backups grouped by storage file kind
     */
    public Map<StorageFileKind, List<StorageBackup>> listBackups() {
        EnumMap<StorageFileKind, List<StorageBackup>> backups = new EnumMap<>(StorageFileKind.class);
        for (StorageFileKind kind : liveFiles.keySet()) {
            backups.put(kind, listBackups(kind));
        }
        return Collections.unmodifiableMap(backups);
    }

    /**
     * Lists backups for one storage file kind, newest first.
     *
     * @param kind storage file kind
     * @return backup descriptors
     */
    public List<StorageBackup> listBackups(StorageFileKind kind) {
        StorageFileKind requiredKind = Objects.requireNonNull(kind, "kind");
        Path backupDirectory = backupDirectory(requiredKind);
        if (Files.notExists(backupDirectory)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.list(backupDirectory)) {
            return paths.filter(Files::isRegularFile).map(path -> toBackup(requiredKind, path)).filter(Optional::isPresent).map(Optional::orElseThrow)
                    .sorted(Comparator.comparing(StorageBackup::fileName).reversed()).toList();
        } catch (NoSuchFileException exception) {
            return List.of();
        } catch (IOException exception) {
            throw new PermissionStorageException("Failed to list " + requiredKind.token() + " backups from " + backupDirectory, exception);
        }
    }

    /**
     * Creates a backup of the current live file when it exists.
     *
     * @param kind storage file kind
     * @return created backup descriptor, or empty when the live file is missing
     */
    public Optional<StorageBackup> backupExistingFile(StorageFileKind kind) {
        StorageFileKind requiredKind = Objects.requireNonNull(kind, "kind");
        Path liveFile = liveFile(requiredKind);
        if (Files.notExists(liveFile)) {
            return Optional.empty();
        }

        Path backupDirectory = backupDirectory(requiredKind);
        try {
            Files.createDirectories(backupDirectory);
            Path backupFile = uniqueBackupPath(requiredKind, backupDirectory, clock.instant());
            Files.copy(liveFile, backupFile, StandardCopyOption.COPY_ATTRIBUTES);
            pruneBackups(requiredKind);
            return Optional.of(new StorageBackup(requiredKind, backupFile.getFileName().toString(), backupFile));
        } catch (IOException exception) {
            throw new PermissionStorageException("Failed to create " + requiredKind.token() + " backup for " + liveFile, exception);
        }
    }

    /**
     * Restores one backup file, applies the restored storage, and rolls the live file back if applying fails.
     *
     * @param kind storage file kind
     * @param backupFileName backup filename from {@link #listBackups(StorageFileKind)}
     * @param applyRestoredStorage action that reloads and refreshes restored storage
     */
    public void restoreBackup(StorageFileKind kind, String backupFileName, Runnable applyRestoredStorage) {
        StorageFileKind requiredKind = Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(applyRestoredStorage, "applyRestoredStorage");
        Path backupFile = resolveBackupFile(requiredKind, backupFileName);
        Path liveFile = liveFile(requiredKind);
        Path parentDirectory = liveFile.getParent();
        Path rollbackFile = null;
        Path restoreFile = null;
        boolean liveFileExisted = Files.exists(liveFile);
        try {
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }
            if (liveFileExisted) {
                rollbackFile = parentDirectory == null
                        ? Files.createTempFile(liveFile.getFileName().toString(), ".restore")
                        : Files.createTempFile(parentDirectory, liveFile.getFileName().toString(), ".restore");
                Files.copy(liveFile, rollbackFile, StandardCopyOption.REPLACE_EXISTING);
            }

            restoreFile = parentDirectory == null
                    ? Files.createTempFile(liveFile.getFileName().toString(), ".restore")
                    : Files.createTempFile(parentDirectory, liveFile.getFileName().toString(), ".restore");
            Files.copy(backupFile, restoreFile, StandardCopyOption.REPLACE_EXISTING);
            StorageFiles.moveAtomically(restoreFile, liveFile);
            restoreFile = null;
            try {
                applyRestoredStorage.run();
            } catch (RuntimeException exception) {
                rollbackLiveFile(liveFile, rollbackFile, liveFileExisted, exception);
                throw new PermissionStorageException("Failed to apply restored " + requiredKind.token() + " backup " + backupFile.getFileName(), exception);
            }
        } catch (IOException exception) {
            rollbackLiveFile(liveFile, rollbackFile, liveFileExisted, exception);
            throw new PermissionStorageException("Failed to restore " + requiredKind.token() + " backup " + backupFile.getFileName(), exception);
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

    private Path liveFile(StorageFileKind kind) {
        Path liveFile = liveFiles.get(kind);
        if (liveFile == null) {
            throw new IllegalArgumentException("storage file kind is not configured for backups: " + kind.token());
        }
        return liveFile;
    }

    private Path backupDirectory(StorageFileKind kind) {
        return backupRoot.resolve(kind.token());
    }

    private void pruneBackups(StorageFileKind kind) throws IOException {
        List<StorageBackup> backups = new ArrayList<>(listBackups(kind));
        for (int index = RETENTION_LIMIT; index < backups.size(); index++) {
            Files.deleteIfExists(backups.get(index).path());
        }
    }

    private Path uniqueBackupPath(StorageFileKind kind, Path backupDirectory, Instant instant) {
        String baseName = kind.token() + "-" + BACKUP_TIMESTAMP_FORMATTER.format(instant);
        Path backupFile = backupDirectory.resolve(baseName + ".json");
        int counter = 2;
        while (Files.exists(backupFile)) {
            backupFile = backupDirectory.resolve(baseName + "-" + counter + ".json");
            counter++;
        }
        return backupFile;
    }

    private Path resolveBackupFile(StorageFileKind kind, String backupFileName) {
        String requiredFileName = Objects.requireNonNull(backupFileName, "backupFileName");
        if (requiredFileName.isBlank() || !Path.of(requiredFileName).getFileName().toString().equals(requiredFileName)) {
            throw new IllegalArgumentException("invalid backup filename: " + requiredFileName);
        }

        Path backupFile = backupDirectory(kind).resolve(requiredFileName).toAbsolutePath().normalize();
        if (!backupFile.startsWith(backupDirectory(kind).toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("invalid backup filename: " + requiredFileName);
        }
        if (!Files.isRegularFile(backupFile)) {
            throw new IllegalArgumentException("unknown " + kind.token() + " backup: " + requiredFileName);
        }
        return backupFile;
    }

    private Optional<StorageBackup> toBackup(StorageFileKind kind, Path path) {
        String fileName = path.getFileName().toString();
        if (!fileName.startsWith(kind.token() + "-") || !fileName.endsWith(".json")) {
            return Optional.empty();
        }
        return Optional.of(new StorageBackup(kind, fileName, path));
    }

    private void rollbackLiveFile(Path liveFile, Path rollbackFile, boolean liveFileExisted, Throwable restoreFailure) {
        try {
            if (liveFileExisted) {
                if (rollbackFile == null) {
                    return;
                }
                Files.copy(Objects.requireNonNull(rollbackFile, "rollbackFile"), liveFile, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(liveFile);
            }
        } catch (IOException rollbackFailure) {
            PermissionStorageException failure = new PermissionStorageException("Failed to roll back live file " + liveFile + " after restore failure", restoreFailure);
            failure.addSuppressed(rollbackFailure);
            throw failure;
        }
    }

    private static Map<StorageFileKind, Path> normalizeLiveFiles(Map<StorageFileKind, Path> liveFiles) {
        Objects.requireNonNull(liveFiles, "liveFiles");
        EnumMap<StorageFileKind, Path> normalized = new EnumMap<>(StorageFileKind.class);
        liveFiles.forEach((kind, path) -> normalized.put(Objects.requireNonNull(kind, "kind"), Objects.requireNonNull(path, "path").toAbsolutePath().normalize()));
        return Collections.unmodifiableMap(normalized);
    }
}
