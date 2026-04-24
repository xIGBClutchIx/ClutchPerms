package me.clutchy.clutchperms.common.command;

import java.util.Objects;

/**
 * Platform-provided status details shown by the shared ClutchPerms diagnostics command.
 *
 * @param permissionsFile path to the persisted direct permission assignments
 * @param subjectsFile path to the persisted subject metadata
 * @param groupsFile path to the persisted group definitions and memberships
 * @param runtimeBridgeStatus platform runtime permission bridge status
 */
public record CommandStatusDiagnostics(String permissionsFile, String subjectsFile, String groupsFile, String runtimeBridgeStatus) {

    /**
     * Creates immutable command status diagnostics.
     *
     * @throws IllegalArgumentException when a diagnostic value is blank
     */
    public CommandStatusDiagnostics {
        permissionsFile = requireNonBlank(permissionsFile, "permissionsFile");
        subjectsFile = requireNonBlank(subjectsFile, "subjectsFile");
        groupsFile = requireNonBlank(groupsFile, "groupsFile");
        runtimeBridgeStatus = requireNonBlank(runtimeBridgeStatus, "runtimeBridgeStatus");
    }

    private static String requireNonBlank(String value, String name) {
        String required = Objects.requireNonNull(value, name);
        if (required.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return required;
    }
}
