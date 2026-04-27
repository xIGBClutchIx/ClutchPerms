package me.clutchy.clutchperms.common.command;

import java.util.Objects;

/**
 * Platform-provided status details shown by the shared ClutchPerms diagnostics command.
 *
 * @param databaseFile path to the persisted SQLite database
 * @param runtimeBridgeStatus platform runtime permission bridge status
 * @param configFile path to the runtime config file
 */
public record CommandStatusDiagnostics(String databaseFile, String runtimeBridgeStatus, String configFile) {

    /**
     * Creates command status diagnostics without a known config file path.
     *
     * @param databaseFile path to the persisted SQLite database
     * @param runtimeBridgeStatus platform runtime bridge status
     */
    public CommandStatusDiagnostics(String databaseFile, String runtimeBridgeStatus) {
        this(databaseFile, runtimeBridgeStatus, "unknown");
    }

    /**
     * Creates immutable command status diagnostics.
     *
     * @throws IllegalArgumentException when a diagnostic value is blank
     */
    public CommandStatusDiagnostics {
        databaseFile = requireNonBlank(databaseFile, "databaseFile");
        runtimeBridgeStatus = requireNonBlank(runtimeBridgeStatus, "runtimeBridgeStatus");
        configFile = requireNonBlank(configFile, "configFile");
    }

    private static String requireNonBlank(String value, String name) {
        String required = Objects.requireNonNull(value, name);
        if (required.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return required;
    }
}
