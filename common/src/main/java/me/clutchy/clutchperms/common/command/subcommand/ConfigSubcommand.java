package me.clutchy.clutchperms.common.command;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.permission.PermissionNodes;

final class ConfigSubcommand<S> {

    private final CommandSupport<S> support;

    private final ClutchPermsCommandEnvironment<S> environment;

    ConfigSubcommand(CommandSupport<S> support) {
        this.support = support;
        this.environment = support.environment();
    }

    LiteralArgumentBuilder<S> builder() {
        return LiteralArgumentBuilder.<S>literal("config")
                .requires(source -> support.canUseAny(source, PermissionNodes.ADMIN_CONFIG_VIEW, PermissionNodes.ADMIN_CONFIG_SET, PermissionNodes.ADMIN_CONFIG_RESET))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_CONFIG_VIEW, ignored -> list(context)))
                .then(LiteralArgumentBuilder.<S>literal("list").requires(source -> support.canUse(source, PermissionNodes.ADMIN_CONFIG_VIEW))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_CONFIG_VIEW, ignored -> list(context))))
                .then(LiteralArgumentBuilder.<S>literal("get").requires(source -> support.canUse(source, PermissionNodes.ADMIN_CONFIG_VIEW))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_CONFIG_VIEW, ignored -> getUsage(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.CONFIG_KEY, StringArgumentType.word()).suggests(this::suggestConfigKeys)
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_CONFIG_VIEW, ignored -> get(context)))))
                .then(LiteralArgumentBuilder.<S>literal("set").requires(source -> support.canUse(source, PermissionNodes.ADMIN_CONFIG_SET))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_CONFIG_SET, ignored -> setKeyUsage(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.CONFIG_KEY, StringArgumentType.word()).suggests(this::suggestConfigKeys)
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_CONFIG_SET, ignored -> setValueUsage(context)))
                                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.CONFIG_VALUE, StringArgumentType.greedyString())
                                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_CONFIG_SET, ignored -> set(context))))))
                .then(LiteralArgumentBuilder.<S>literal("reset").requires(source -> support.canUse(source, PermissionNodes.ADMIN_CONFIG_RESET))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_CONFIG_RESET, ignored -> resetUsage(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.CONFIG_KEY, StringArgumentType.word()).suggests(this::suggestConfigResetKeys)
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_CONFIG_RESET, ignored -> reset(context)))))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.UNKNOWN, StringArgumentType.word())
                        .requires(source -> support.canUse(source, PermissionNodes.ADMIN_CONFIG_VIEW))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_CONFIG_VIEW, ignored -> unknownUsage(context))));
    }

    private int usage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing config command.", "Config commands inspect or update runtime config.", CommandCatalogs.configUsages());
    }

    private int list(CommandContext<S> context) {
        ClutchPermsConfig config = environment.config();
        environment.sendMessage(context.getSource(), CommandLang.configHeader());
        CommandCatalogs.CONFIG_ENTRIES
                .forEach(entry -> environment.sendMessage(context.getSource(), CommandLang.configRow(entry.key(), entry.value(config), entry.description(), entry.displayHint())));
        return Command.SINGLE_SUCCESS;
    }

    private int getUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing config key.", "Choose one config value to inspect.", List.of("config get <key>"));
    }

    private int get(CommandContext<S> context) throws CommandSyntaxException {
        CommandCatalogs.ConfigEntry entry = support.getConfigEntry(context);
        environment.sendMessage(context.getSource(), CommandLang.configGet(entry.key(), entry.value(environment.config()), entry.description(), entry.displayHint()));
        return Command.SINGLE_SUCCESS;
    }

    private int setKeyUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing config key.", "Choose one config value to update.", List.of("config set <key> <value>"));
    }

    private int setValueUsage(CommandContext<S> context) throws CommandSyntaxException {
        CommandCatalogs.ConfigEntry entry = support.getConfigEntry(context);
        return support.sendUsage(context, "Missing config value.", "Set " + entry.key() + " to " + entry.inputHint() + ".", List.of("config set " + entry.key() + " <value>"));
    }

    private int set(CommandContext<S> context) throws CommandSyntaxException {
        CommandCatalogs.ConfigEntry entry = support.getConfigEntry(context);
        String rawValue = StringArgumentType.getString(context, CommandArguments.CONFIG_VALUE);
        String newValue = support.parseConfigValue(context, entry, rawValue);
        return setConfigValue(context, entry, newValue);
    }

    private int resetUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing config key.", "Reset one config value, or all config values, to defaults.", List.of("config reset <key|all>"));
    }

    private int reset(CommandContext<S> context) throws CommandSyntaxException {
        String key = StringArgumentType.getString(context, CommandArguments.CONFIG_KEY);
        if ("all".equalsIgnoreCase(key)) {
            return resetAll(context);
        }

        CommandCatalogs.ConfigEntry entry = support.getConfigEntry(context);
        String oldValue = entry.value(environment.config());
        String defaultValue = entry.defaultValue();
        if (oldValue.equals(defaultValue)) {
            environment.sendMessage(context.getSource(), CommandLang.configAlreadySet(entry.key(), oldValue));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().configSnapshot(environment.config());

        try {
            environment.updateConfig(config -> entry.withValue(config, defaultValue));
            environment.refreshRuntimePermissions();
        } catch (RuntimeException exception) {
            throw support.configOperationFailed(exception);
        }
        support.audit().recordAudit(context, "config.reset", "config", entry.key(), entry.key(), beforeJson, support.audit().configSnapshot(environment.config()), true);
        environment.sendMessage(context.getSource(), CommandLang.configReset(entry.key(), oldValue, defaultValue));
        return Command.SINGLE_SUCCESS;
    }

    private int unknownUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Unknown config command: " + StringArgumentType.getString(context, CommandArguments.UNKNOWN),
                "Config commands inspect and update runtime config.", CommandCatalogs.configUsages());
    }

    private CompletableFuture<Suggestions> suggestConfigKeys(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        CommandCatalogs.CONFIG_KEYS.stream().filter(key -> key.toLowerCase(java.util.Locale.ROOT).startsWith(remaining)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestConfigResetKeys(CommandContext<S> context, SuggestionsBuilder builder) {
        suggestConfigKeys(context, builder);
        if ("all".startsWith(builder.getRemainingLowerCase())) {
            builder.suggest("all");
        }
        return builder.buildFuture();
    }

    private int setConfigValue(CommandContext<S> context, CommandCatalogs.ConfigEntry entry, String newValue) throws CommandSyntaxException {
        String oldValue = entry.value(environment.config());
        if (oldValue.equals(newValue)) {
            environment.sendMessage(context.getSource(), CommandLang.configAlreadySet(entry.key(), oldValue));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().configSnapshot(environment.config());

        try {
            environment.updateConfig(config -> entry.withValue(config, newValue));
            environment.refreshRuntimePermissions();
        } catch (RuntimeException exception) {
            throw support.configOperationFailed(exception);
        }
        support.audit().recordAudit(context, "config.set", "config", entry.key(), entry.key(), beforeJson, support.audit().configSnapshot(environment.config()), true);
        environment.sendMessage(context.getSource(), CommandLang.configUpdated(entry.key(), oldValue, newValue));
        return Command.SINGLE_SUCCESS;
    }

    private int resetAll(CommandContext<S> context) throws CommandSyntaxException {
        ClutchPermsConfig beforeConfig = environment.config();
        if (beforeConfig.equals(ClutchPermsConfig.defaults())) {
            environment.sendMessage(context.getSource(), CommandLang.configAlreadyDefaults());
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().configSnapshot(beforeConfig);

        try {
            environment.updateConfig(ignored -> ClutchPermsConfig.defaults());
            environment.refreshRuntimePermissions();
        } catch (RuntimeException exception) {
            throw support.configOperationFailed(exception);
        }
        support.audit().recordAudit(context, "config.reset-all", "config", "all", "all config", beforeJson, support.audit().configSnapshot(environment.config()), true);
        environment.sendMessage(context.getSource(), CommandLang.configResetAll());
        return Command.SINGLE_SUCCESS;
    }
}
