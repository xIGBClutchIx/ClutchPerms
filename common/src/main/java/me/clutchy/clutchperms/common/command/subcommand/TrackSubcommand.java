package me.clutchy.clutchperms.common.command.subcommand;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import me.clutchy.clutchperms.common.command.ClutchPermsCommandEnvironment;
import me.clutchy.clutchperms.common.command.CommandArguments;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.permission.PermissionNodes;

/**
 * Builds the `/clutchperms track` command branch.
 */
public final class TrackSubcommand {

    /**
     * Handlers for track definition and ordered-group command actions.
     *
     * @param <S> platform command source type
     */
    public interface Handlers<S> {

        int rootUsage(CommandContext<S> context) throws CommandSyntaxException;

        int list(CommandContext<S> context) throws CommandSyntaxException;

        int targetUsage(CommandContext<S> context) throws CommandSyntaxException;

        int unknownTargetUsage(CommandContext<S> context) throws CommandSyntaxException;

        int create(CommandContext<S> context) throws CommandSyntaxException;

        int delete(CommandContext<S> context) throws CommandSyntaxException;

        int renameUsage(CommandContext<S> context) throws CommandSyntaxException;

        int rename(CommandContext<S> context) throws CommandSyntaxException;

        int info(CommandContext<S> context) throws CommandSyntaxException;

        int show(CommandContext<S> context) throws CommandSyntaxException;

        int appendUsage(CommandContext<S> context) throws CommandSyntaxException;

        int append(CommandContext<S> context) throws CommandSyntaxException;

        int insertUsage(CommandContext<S> context) throws CommandSyntaxException;

        int insert(CommandContext<S> context) throws CommandSyntaxException;

        int moveUsage(CommandContext<S> context) throws CommandSyntaxException;

        int move(CommandContext<S> context) throws CommandSyntaxException;

        int removeUsage(CommandContext<S> context) throws CommandSyntaxException;

        int remove(CommandContext<S> context) throws CommandSyntaxException;
    }

    public static <S> LiteralArgumentBuilder<S> builder(ClutchPermsCommandEnvironment<S> environment, AuthorizedCommand<S> authorized, Handlers<S> handlers) {
        RequiredArgumentBuilder<S, String> track = trackArgument(environment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_VIEW, handlers::targetUsage))
                .requires(source -> authorized.canUseAny(source, trackPermissions()))
                .then(authorized.literal("create", PermissionNodes.ADMIN_TRACK_CREATE)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_CREATE, handlers::create)))
                .then(authorized.literal("delete", PermissionNodes.ADMIN_TRACK_DELETE)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_DELETE, handlers::delete)))
                .then(renameCommand(authorized, handlers))
                .then(authorized.literal("info", PermissionNodes.ADMIN_TRACK_INFO).executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_INFO, handlers::info)))
                .then(authorized.literal("list", PermissionNodes.ADMIN_TRACK_VIEW).executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_VIEW, handlers::show))
                        .then(TrackSubcommand.<S>pageArgument().executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_VIEW, handlers::show))))
                .then(authorized.literal("append", PermissionNodes.ADMIN_TRACK_APPEND)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_APPEND, handlers::appendUsage))
                        .then(groupArgument(environment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_APPEND, handlers::append))))
                .then(authorized.literal("insert", PermissionNodes.ADMIN_TRACK_INSERT)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_INSERT, handlers::insertUsage))
                        .then(TrackSubcommand.<S>positionArgument()
                                .then(groupArgument(environment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_INSERT, handlers::insert)))))
                .then(authorized.literal("move", PermissionNodes.ADMIN_TRACK_MOVE)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_MOVE, handlers::moveUsage))
                        .then(existingTrackGroupArgument(environment)
                                .then(TrackSubcommand.<S>positionArgument().executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_MOVE, handlers::move)))))
                .then(authorized.literal("remove", PermissionNodes.ADMIN_TRACK_REMOVE)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_REMOVE, handlers::removeUsage))
                        .then(existingTrackGroupArgument(environment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_REMOVE, handlers::remove))))
                .then(authorized.requires(CommandArguments.<S>unknown(), PermissionNodes.ADMIN_TRACK_VIEW)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_VIEW, handlers::unknownTargetUsage)));

        return authorized.branch("track", trackPermissions()).executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_LIST, handlers::rootUsage))
                .then(authorized.literal("list", PermissionNodes.ADMIN_TRACK_LIST).executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_LIST, handlers::list))
                        .then(TrackSubcommand.<S>pageArgument().executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_LIST, handlers::list))))
                .then(track);
    }

    private static <S> LiteralArgumentBuilder<S> renameCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers) {
        return authorized.literal("rename", PermissionNodes.ADMIN_TRACK_RENAME)
                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_RENAME, handlers::renameUsage))
                .then(TrackSubcommand.<S>newTrackArgument().executes(context -> authorized.run(context, PermissionNodes.ADMIN_TRACK_RENAME, handlers::rename)));
    }

    private static String[] trackPermissions() {
        return new String[]{PermissionNodes.ADMIN_TRACK_LIST, PermissionNodes.ADMIN_TRACK_INFO, PermissionNodes.ADMIN_TRACK_CREATE, PermissionNodes.ADMIN_TRACK_DELETE,
                PermissionNodes.ADMIN_TRACK_RENAME, PermissionNodes.ADMIN_TRACK_VIEW, PermissionNodes.ADMIN_TRACK_APPEND, PermissionNodes.ADMIN_TRACK_INSERT,
                PermissionNodes.ADMIN_TRACK_MOVE, PermissionNodes.ADMIN_TRACK_REMOVE};
    }

    private static <S> RequiredArgumentBuilder<S, String> trackArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.TRACK, StringArgumentType.word()).suggests((context, builder) -> suggestTracks(environment, builder));
    }

    private static <S> RequiredArgumentBuilder<S, String> newTrackArgument() {
        return RequiredArgumentBuilder.argument(CommandArguments.NEW_TRACK, StringArgumentType.word());
    }

    private static <S> RequiredArgumentBuilder<S, Integer> positionArgument() {
        return RequiredArgumentBuilder.argument(CommandArguments.POSITION, IntegerArgumentType.integer(1));
    }

    private static <S> RequiredArgumentBuilder<S, String> groupArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.GROUP, StringArgumentType.word())
                .suggests((context, builder) -> suggestAvailableGroups(environment, context, builder));
    }

    private static <S> RequiredArgumentBuilder<S, String> existingTrackGroupArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.GROUP, StringArgumentType.word())
                .suggests((context, builder) -> suggestTrackGroups(environment, context, builder));
    }

    private static <S> RequiredArgumentBuilder<S, String> pageArgument() {
        return RequiredArgumentBuilder.argument(CommandArguments.PAGE, StringArgumentType.word());
    }

    private static <S> CompletableFuture<Suggestions> suggestTracks(ClutchPermsCommandEnvironment<S> environment, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        environment.trackService().getTracks().stream().sorted(Comparator.naturalOrder()).filter(track -> track.startsWith(remaining)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static <S> CompletableFuture<Suggestions> suggestAvailableGroups(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        List<String> existingTrackGroups;
        try {
            existingTrackGroups = environment.trackService().getTrackGroups(StringArgumentType.getString(context, CommandArguments.TRACK));
        } catch (IllegalArgumentException exception) {
            existingTrackGroups = List.of();
        }
        List<String> trackGroups = existingTrackGroups;
        environment.groupService().getGroups().stream().sorted(Comparator.naturalOrder()).filter(group -> group.startsWith(remaining))
                .filter(group -> !GroupService.OP_GROUP.equals(group)).filter(group -> !trackGroups.contains(group)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static <S> CompletableFuture<Suggestions> suggestTrackGroups(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        try {
            environment.trackService().getTrackGroups(StringArgumentType.getString(context, CommandArguments.TRACK)).stream().filter(group -> group.startsWith(remaining))
                    .forEach(builder::suggest);
        } catch (IllegalArgumentException ignored) {
            // Suggestions stay empty when the track is not yet known.
        }
        return builder.buildFuture();
    }

    private TrackSubcommand() {
    }
}
