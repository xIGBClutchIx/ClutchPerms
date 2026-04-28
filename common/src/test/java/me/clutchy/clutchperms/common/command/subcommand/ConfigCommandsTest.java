package me.clutchy.clutchperms.common.command;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.clutchy.clutchperms.common.config.ClutchPermsAuditRetentionConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsBackupConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsChatConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsCommandConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsPaperConfig;
import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies shared Brigadier command behavior independent of a server platform.
 */
class ConfigCommandsTest extends CommandTestBase {

    /**
     * Confirms config list and get show active runtime config values.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void configCommandsListAndGetValues() throws CommandSyntaxException {
        environment.setConfig(
                new ClutchPermsConfig(new ClutchPermsBackupConfig(3), new ClutchPermsCommandConfig(4, 5), new ClutchPermsChatConfig(false), new ClutchPermsPaperConfig(false)));
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms config", console);
        dispatcher.execute("clutchperms config get commands.helpPageSize", console);
        dispatcher.execute("clutchperms config get audit.retention.maxAgeDays", console);
        dispatcher.execute("clutchperms config get chat.enabled", console);
        dispatcher.execute("clutchperms config get paper.replaceOpCommands", console);

        assertEquals(List.of("ClutchPerms config:", "backups.retentionLimit = 3 (newest database backups kept; range 1-1000)",
                "backups.schedule.enabled = false (automatic database backups; values true/false or on/off)",
                "backups.schedule.intervalMinutes = 60 (minutes between automatic database backups; range 5-10080)",
                "backups.schedule.runOnStartup = false (startup database backup; values true/false or on/off)",
                "audit.retention.enabled = true (automatic audit history retention pruning; values true/false or on/off)",
                "audit.retention.maxAgeDays = 90 (audit history days kept; range 1-3650)",
                "audit.retention.maxEntries = 0 (newest audit history entries kept; 0 disables count retention; range 0-1000000)",
                "commands.helpPageSize = 4 (command rows shown per help page; range 1-50)", "commands.resultPageSize = 5 (rows shown per list-result page; range 1-50)",
                "chat.enabled = false (prefix and suffix chat formatting; values true/false or on/off)",
                "paper.replaceOpCommands = false (Paper /op and /deop ClutchPerms replacements; values true/false or on/off)",
                "commands.helpPageSize = 4 (command rows shown per help page; range 1-50)", "audit.retention.maxAgeDays = 90 (audit history days kept; range 1-3650)",
                "chat.enabled = false (prefix and suffix chat formatting; values true/false or on/off)",
                "paper.replaceOpCommands = false (Paper /op and /deop ClutchPerms replacements; values true/false or on/off)"), console.messages());
    }

    /**
     * Confirms config set applies immediately to active command behavior.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void configSetUpdatesValuesAndReloadsRuntimeConfig() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms config set backups.retentionLimit 3", console);
        dispatcher.execute("clutchperms config set audit.retention.enabled off", console);
        dispatcher.execute("clutchperms config set audit.retention.maxAgeDays 30", console);
        dispatcher.execute("clutchperms config set audit.retention.maxEntries 100", console);
        dispatcher.execute("clutchperms config set commands.helpPageSize 3", console);
        dispatcher.execute("clutchperms config set commands.resultPageSize 2", console);
        dispatcher.execute("clutchperms config set chat.enabled off", console);
        dispatcher.execute("clutchperms config set paper.replaceOpCommands disabled", console);

        assertEquals(new ClutchPermsConfig(new ClutchPermsBackupConfig(3), new ClutchPermsAuditRetentionConfig(false, 30, 100), new ClutchPermsCommandConfig(3, 2),
                new ClutchPermsChatConfig(false), new ClutchPermsPaperConfig(false)), environment.config());
        assertEquals(8, environment.configUpdates());
        assertEquals(8, environment.runtimeRefreshes());
        assertTrue(console.messages().contains("Updated config backups.retentionLimit: 10 -> 3. Runtime reloaded."));
        assertTrue(console.messages().contains("Updated config audit.retention.enabled: true -> false. Runtime reloaded."));
        assertTrue(console.messages().contains("Updated config audit.retention.maxAgeDays: 90 -> 30. Runtime reloaded."));
        assertTrue(console.messages().contains("Updated config audit.retention.maxEntries: 0 -> 100. Runtime reloaded."));
        assertTrue(console.messages().contains("Updated config commands.helpPageSize: 7 -> 3. Runtime reloaded."));
        assertTrue(console.messages().contains("Updated config commands.resultPageSize: 8 -> 2. Runtime reloaded."));
        assertTrue(console.messages().contains("Updated config chat.enabled: true -> false. Runtime reloaded."));
        assertTrue(console.messages().contains("Updated config paper.replaceOpCommands: true -> false. Runtime reloaded."));

        TestSource help = TestSource.console();
        dispatcher.execute("clutchperms", help);
        assertEquals(List.of("ClutchPerms commands (page 1/22):", "/clutchperms help [page]", "/clutchperms status", "/clutchperms reload", "Page 1/22 | Next >"), help.messages());

        permissionService.setPermission(TARGET_ID, "example.01", PermissionValue.TRUE);
        permissionService.setPermission(TARGET_ID, "example.02", PermissionValue.TRUE);
        permissionService.setPermission(TARGET_ID, "example.03", PermissionValue.TRUE);
        TestSource list = TestSource.console();
        dispatcher.execute("clutchperms user Target list 2", list);
        assertEquals(List.of("Permissions for Target (00000000-0000-0000-0000-000000000002) (page 2/2):", "  example.03=TRUE", "< Prev | Page 2/2"), list.messages());
    }

    /**
     * Confirms config reset supports one key and all keys.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void configResetRestoresDefaults() throws CommandSyntaxException {
        environment.setConfig(new ClutchPermsConfig(new ClutchPermsBackupConfig(3), new ClutchPermsCommandConfig(4, 5), new ClutchPermsChatConfig(false)));
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms config reset backups.retentionLimit", console);
        dispatcher.execute("clutchperms config reset all", console);

        assertEquals(ClutchPermsConfig.defaults(), environment.config());
        assertEquals(2, environment.configUpdates());
        assertEquals(2, environment.runtimeRefreshes());
        assertEquals(List.of("Reset config backups.retentionLimit: 3 -> 10. Runtime reloaded.", "Reset all config values to defaults. Runtime reloaded."), console.messages());
    }

    /**
     * Confirms same-value config changes succeed without writing or refreshing runtime state.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void configSameValueChangesAvoidReload() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms config set backups.retentionLimit 10", console);
        dispatcher.execute("clutchperms config set chat.enabled on", console);
        dispatcher.execute("clutchperms config set paper.replaceOpCommands true", console);
        dispatcher.execute("clutchperms config reset all", console);

        assertEquals(ClutchPermsConfig.defaults(), environment.config());
        assertEquals(0, environment.configUpdates());
        assertEquals(0, environment.runtimeRefreshes());
        assertEquals(List.of("Config backups.retentionLimit is already 10.", "Config chat.enabled is already true.", "Config paper.replaceOpCommands is already true.",
                "Config already matches defaults."), console.messages());
    }

    /**
     * Confirms config command failures are styled and do not apply changes.
     */
    @Test
    void configCommandsRejectBadKeysValuesAndUpdateFailures() {
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms config get commands.help", console, "Unknown config key: commands.help");
        assertCommandFails("clutchperms config set commands.helpPageSize nope", console, "Invalid config value for commands.helpPageSize: nope");
        assertCommandFails("clutchperms config set commands.helpPageSize 51", console, "commands.helpPageSize must be an integer between 1 and 50.");
        assertCommandFails("clutchperms config set audit.retention.enabled maybe", console, "audit.retention.enabled must be true/false or on/off.");
        assertCommandFails("clutchperms config set audit.retention.maxAgeDays 0", console, "audit.retention.maxAgeDays must be an integer between 1 and 3650.");
        assertCommandFails("clutchperms config set audit.retention.maxEntries 1000001", console, "audit.retention.maxEntries must be an integer between 0 and 1000000.");
        assertCommandFails("clutchperms config set chat.enabled maybe", console, "chat.enabled must be true/false or on/off.");
        assertCommandFails("clutchperms config set paper.replaceOpCommands maybe", console, "paper.replaceOpCommands must be true/false or on/off.");

        environment.failConfigUpdate(new PermissionStorageException("disk blocked"));
        assertCommandFails("clutchperms config set backups.retentionLimit 3", console, "Config operation failed: disk blocked");

        assertEquals(ClutchPermsConfig.defaults(), environment.config());
        assertEquals(0, environment.configUpdates());
        assertEquals(0, environment.runtimeRefreshes());
    }

    /**
     * Confirms config view, set, and reset use separate command permissions.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void configCommandsUseGranularPermissions() throws CommandSyntaxException {
        TestSource player = TestSource.player(ADMIN_ID);

        assertCommandUnavailable("clutchperms config list", player);
        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_CONFIG_VIEW, PermissionValue.TRUE);
        assertEquals(1, dispatcher.execute("clutchperms config list", player));
        assertCommandUnavailable("clutchperms config set backups.retentionLimit 3", player);

        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_CONFIG_SET, PermissionValue.TRUE);
        assertEquals(1, dispatcher.execute("clutchperms config set backups.retentionLimit 3", player));
        assertCommandUnavailable("clutchperms config reset backups.retentionLimit", player);

        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_CONFIG_RESET, PermissionValue.TRUE);
        assertEquals(1, dispatcher.execute("clutchperms config reset backups.retentionLimit", player));
    }

}
