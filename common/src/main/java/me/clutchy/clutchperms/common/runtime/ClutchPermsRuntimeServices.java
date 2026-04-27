package me.clutchy.clutchperms.common.runtime;

import java.util.Objects;

import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.node.MutablePermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistry;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.storage.SqliteStore;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;

/**
 * Immutable snapshot of active ClutchPerms services.
 *
 * @param permissionService direct permission service
 * @param subjectMetadataService subject metadata service
 * @param groupService group service
 * @param manualPermissionNodeRegistry manual known-node registry
 * @param permissionNodeRegistry merged known-node registry
 * @param permissionResolver effective permission resolver
 * @param config active runtime config
 * @param sqliteStore active SQLite store
 */
public record ClutchPermsRuntimeServices(PermissionService permissionService, SubjectMetadataService subjectMetadataService, GroupService groupService,
        MutablePermissionNodeRegistry manualPermissionNodeRegistry, PermissionNodeRegistry permissionNodeRegistry, PermissionResolver permissionResolver, ClutchPermsConfig config,
        SqliteStore sqliteStore) implements AutoCloseable {

    /**
     * Validates active services.
     */
    public ClutchPermsRuntimeServices {
        permissionService = Objects.requireNonNull(permissionService, "permissionService");
        subjectMetadataService = Objects.requireNonNull(subjectMetadataService, "subjectMetadataService");
        groupService = Objects.requireNonNull(groupService, "groupService");
        manualPermissionNodeRegistry = Objects.requireNonNull(manualPermissionNodeRegistry, "manualPermissionNodeRegistry");
        permissionNodeRegistry = Objects.requireNonNull(permissionNodeRegistry, "permissionNodeRegistry");
        permissionResolver = Objects.requireNonNull(permissionResolver, "permissionResolver");
        config = Objects.requireNonNull(config, "config");
        sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
    }

    @Override
    public void close() {
        sqliteStore.close();
    }
}
