package me.clutchy.clutchperms.common.command.subcommand;

import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import me.clutchy.clutchperms.common.command.CommandArguments;
import me.clutchy.clutchperms.common.permission.PermissionNodes;

/**
 * Builds the `/clutchperms config` command branch.
 */
public final class ConfigSubcommand {

    /**
     * Handlers for config command actions.
     *
     * @param <S> platform command source type
     */
    public interface Handlers<S> {

        int usage(CommandContext<S> context) throws CommandSyntaxException;

        int list(CommandContext<S> context) throws CommandSyntaxException;

        int getUsage(CommandContext<S> context) throws CommandSyntaxException;

        int get(CommandContext<S> context) throws CommandSyntaxException;

        int setKeyUsage(CommandContext<S> context) throws CommandSyntaxException;

        int setValueUsage(CommandContext<S> context) throws CommandSyntaxException;

        int set(CommandContext<S> context) throws CommandSyntaxException;

        int resetUsage(CommandContext<S> context) throws CommandSyntaxException;

        int reset(CommandContext<S> context) throws CommandSyntaxException;

        int unknownUsage(CommandContext<S> context) throws CommandSyntaxException;
    }

    public static <S> LiteralArgumentBuilder<S> builder(AuthorizedCommand<S> authorized, Handlers<S> handlers, Collection<String> configKeys) {
        return authorized.branch("config", PermissionNodes.ADMIN_CONFIG_VIEW, PermissionNodes.ADMIN_CONFIG_SET, PermissionNodes.ADMIN_CONFIG_RESET)
                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_CONFIG_VIEW, handlers::list))
                .then(authorized.literal("list", PermissionNodes.ADMIN_CONFIG_VIEW).executes(context -> authorized.run(context, PermissionNodes.ADMIN_CONFIG_VIEW, handlers::list)))
                .then(authorized.literal("get", PermissionNodes.ADMIN_CONFIG_VIEW)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_CONFIG_VIEW, handlers::getUsage))
                        .then(ConfigSubcommand.<S>configKeyArgument(configKeys).executes(context -> authorized.run(context, PermissionNodes.ADMIN_CONFIG_VIEW, handlers::get))))
                .then(authorized.literal("set", PermissionNodes.ADMIN_CONFIG_SET)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_CONFIG_SET, handlers::setKeyUsage))
                        .then(ConfigSubcommand.<S>configKeyArgument(configKeys)
                                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_CONFIG_SET, handlers::setValueUsage))
                                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.CONFIG_VALUE, StringArgumentType.word())
                                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_CONFIG_SET, handlers::set)))))
                .then(authorized.literal("reset", PermissionNodes.ADMIN_CONFIG_RESET)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_CONFIG_RESET, handlers::resetUsage))
                        .then(ConfigSubcommand.<S>configResetArgument(configKeys)
                                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_CONFIG_RESET, handlers::reset))))
                .then(authorized.requires(CommandArguments.<S>unknown(), PermissionNodes.ADMIN_CONFIG_VIEW)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_CONFIG_VIEW, handlers::unknownUsage)));
    }

    private static <S> RequiredArgumentBuilder<S, String> configKeyArgument(Collection<String> configKeys) {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.CONFIG_KEY, StringArgumentType.word())
                .suggests((context, builder) -> suggestConfigKeys(configKeys, builder));
    }

    private static <S> RequiredArgumentBuilder<S, String> configResetArgument(Collection<String> configKeys) {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.CONFIG_KEY, StringArgumentType.word())
                .suggests((context, builder) -> suggestConfigResetTargets(configKeys, builder));
    }

    private static CompletableFuture<Suggestions> suggestConfigResetTargets(Collection<String> configKeys, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        if ("all".startsWith(remaining)) {
            builder.suggest("all");
        }
        return suggestConfigKeys(configKeys, builder);
    }

    private static CompletableFuture<Suggestions> suggestConfigKeys(Collection<String> configKeys, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        configKeys.stream().filter(key -> key.toLowerCase(Locale.ROOT).startsWith(remaining)).sorted().forEach(builder::suggest);
        return builder.buildFuture();
    }

    private ConfigSubcommand() {
    }
}
