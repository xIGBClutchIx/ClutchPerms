package me.clutchy.clutchperms.common.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.clutchy.clutchperms.common.node.PermissionNodeRegistries;
import me.clutchy.clutchperms.common.node.PermissionNodeSource;
import me.clutchy.clutchperms.common.permission.PermissionServices;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.StorageBackup;
import me.clutchy.clutchperms.common.storage.StorageFileKind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the shared platform-neutral storage runtime lifecycle.
 */
class ClutchPermsRuntimeTest {

    private static final UUID SUBJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir
    private Path temporaryDirectory;

    /**
     * Confirms reload loads storage, composes platform known nodes, and materializes missing files.
     */
    @Test
    void reloadLoadsServicesAndMaterializesMissingFiles() {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, () -> PermissionNodeRegistries.staticNodes(PermissionNodeSource.PLATFORM, List.of("platform.node")),
                ClutchPermsRuntimeHooks.noop());

        ClutchPermsRuntimeServices previousServices = runtime.reload();

        assertNull(previousServices);
        assertNotNull(runtime.permissionService());
        assertNotNull(runtime.subjectMetadataService());
        assertNotNull(runtime.groupService());
        assertNotNull(runtime.manualPermissionNodeRegistry());
        assertNotNull(runtime.permissionResolver());
        assertTrue(runtime.permissionNodeRegistry().getKnownNode("platform.node").isPresent());
        assertTrue(Files.exists(storagePaths.permissionsFile()));
        assertTrue(Files.exists(storagePaths.subjectsFile()));
        assertTrue(Files.exists(storagePaths.groupsFile()));
        assertTrue(Files.exists(storagePaths.nodesFile()));
    }

    /**
     * Confirms validation parses disk state without replacing active services.
     */
    @Test
    void validateDoesNotReplaceActiveServices() {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());
        runtime.reload();
        ClutchPermsRuntimeServices activeServices = runtime.services();

        PermissionServices.jsonFile(storagePaths.permissionsFile()).setPermission(SUBJECT_ID, "example.validate", PermissionValue.TRUE);

        runtime.validate();

        assertSame(activeServices, runtime.services());
        assertEquals(PermissionValue.UNSET, runtime.permissionService().getPermission(SUBJECT_ID, "example.validate"));
    }

    /**
     * Confirms failed reload attempts leave the previous runtime snapshot active.
     *
     * @throws IOException if the test file cannot be overwritten
     */
    @Test
    void failedReloadKeepsPreviousServices() throws IOException {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());
        runtime.reload();
        ClutchPermsRuntimeServices activeServices = runtime.services();

        Files.writeString(storagePaths.permissionsFile(), "{not-json");

        assertThrows(PermissionStorageException.class, runtime::reload);
        assertSame(activeServices, runtime.services());
    }

    /**
     * Confirms storage mutation observers invalidate resolver cache and invoke runtime refresh hooks.
     */
    @Test
    void mutationObserversInvalidateResolverCacheAndRefreshRuntimeHooks() {
        List<UUID> refreshedSubjects = new ArrayList<>();
        AtomicInteger fullRefreshes = new AtomicInteger();
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(ClutchPermsStoragePaths.inDirectory(temporaryDirectory),
                new ClutchPermsRuntimeHooks(refreshedSubjects::add, fullRefreshes::incrementAndGet));
        runtime.reload();

        runtime.permissionResolver().resolve(SUBJECT_ID, "example.node");
        assertTrue(runtime.permissionResolver().cacheStats().subjects() > 0);

        runtime.permissionService().setPermission(SUBJECT_ID, "example.node", PermissionValue.TRUE);
        assertEquals(List.of(SUBJECT_ID), refreshedSubjects);
        assertEquals(0, runtime.permissionResolver().cacheStats().subjects());

        runtime.permissionResolver().resolve(SUBJECT_ID, "example.node");
        assertTrue(runtime.permissionResolver().cacheStats().subjects() > 0);
        runtime.groupService().createGroup("staff");
        assertEquals(0, runtime.permissionResolver().cacheStats().subjects());
        assertEquals(1, fullRefreshes.get());

        runtime.permissionResolver().resolve(SUBJECT_ID, "example.node");
        assertTrue(runtime.permissionResolver().cacheStats().subjects() > 0);
        runtime.manualPermissionNodeRegistry().addNode("example.known");
        assertEquals(0, runtime.permissionResolver().cacheStats().subjects());
        assertEquals(2, fullRefreshes.get());
    }

    /**
     * Confirms backup services are created from the shared storage path map and backup root.
     */
    @Test
    void backupServiceUsesStoragePathMapAndBackupRoot() {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());
        runtime.reload();

        StorageBackup backup = runtime.storageBackupService().backupExistingFile(StorageFileKind.PERMISSIONS).orElseThrow();

        assertEquals(List.of(StorageFileKind.PERMISSIONS, StorageFileKind.SUBJECTS, StorageFileKind.GROUPS, StorageFileKind.NODES), runtime.storageBackupService().fileKinds());
        assertTrue(backup.path().startsWith(storagePaths.backupRoot().resolve(StorageFileKind.PERMISSIONS.token())));
    }
}
