package me.clutchy.clutchperms.common.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.permission.PermissionNodes;

final class TrackSubcommand<S> {

    private final CommandSupport<S> support;

    private final ClutchPermsCommandEnvironment<S> environment;

    TrackSubcommand(CommandSupport<S> support) {
        this.support = support;
        this.environment = support.environment();
    }

    LiteralArgumentBuilder<S> builder() {
        RequiredArgumentBuilder<S, String> track = RequiredArgumentBuilder.<S, String>argument(CommandArguments.TRACK, StringArgumentType.word())
                .requires(source -> support.canUseAny(source, trackPermissions())).suggests(this::suggestTracks)
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_VIEW, ignored -> targetUsage(context)))
                .then(LiteralArgumentBuilder.<S>literal("create").requires(source -> support.canUse(source, PermissionNodes.ADMIN_TRACK_CREATE))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_CREATE, ignored -> create(context))))
                .then(LiteralArgumentBuilder.<S>literal("delete").requires(source -> support.canUse(source, PermissionNodes.ADMIN_TRACK_DELETE))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_DELETE, ignored -> delete(context))))
                .then(renameCommand())
                .then(LiteralArgumentBuilder.<S>literal("info").requires(source -> support.canUse(source, PermissionNodes.ADMIN_TRACK_INFO))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_INFO, ignored -> info(context))))
                .then(LiteralArgumentBuilder.<S>literal("list").requires(source -> support.canUse(source, PermissionNodes.ADMIN_TRACK_VIEW))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_VIEW, ignored -> show(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.PAGE, StringArgumentType.word())
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_VIEW, ignored -> show(context)))))
                .then(LiteralArgumentBuilder.<S>literal("append").requires(source -> support.canUse(source, PermissionNodes.ADMIN_TRACK_APPEND))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_APPEND, ignored -> appendUsage(context)))
                        .then(trackGroupArgument().executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_APPEND, ignored -> append(context)))))
                .then(LiteralArgumentBuilder.<S>literal("insert").requires(source -> support.canUse(source, PermissionNodes.ADMIN_TRACK_INSERT))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_INSERT, ignored -> insertUsage(context)))
                        .then(RequiredArgumentBuilder.<S, Integer>argument(CommandArguments.POSITION, IntegerArgumentType.integer(1)).then(
                                trackGroupArgument().executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_INSERT, ignored -> insert(context))))))
                .then(LiteralArgumentBuilder.<S>literal("move").requires(source -> support.canUse(source, PermissionNodes.ADMIN_TRACK_MOVE))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_MOVE, ignored -> moveUsage(context)))
                        .then(existingTrackGroupArgument().then(RequiredArgumentBuilder.<S, Integer>argument(CommandArguments.POSITION, IntegerArgumentType.integer(1))
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_MOVE, ignored -> move(context))))))
                .then(LiteralArgumentBuilder.<S>literal("remove").requires(source -> support.canUse(source, PermissionNodes.ADMIN_TRACK_REMOVE))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_REMOVE, ignored -> removeUsage(context)))
                        .then(existingTrackGroupArgument().executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_REMOVE, ignored -> remove(context)))))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.UNKNOWN, StringArgumentType.word())
                        .requires(source -> support.canUse(source, PermissionNodes.ADMIN_TRACK_VIEW))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_VIEW, ignored -> unknownTargetUsage(context))));

        return LiteralArgumentBuilder.<S>literal("track").requires(source -> support.canUseAny(source, trackPermissions()))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_LIST, ignored -> rootUsage(context)))
                .then(LiteralArgumentBuilder.<S>literal("list").requires(source -> support.canUse(source, PermissionNodes.ADMIN_TRACK_LIST))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_LIST, ignored -> list(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.PAGE, StringArgumentType.word())
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_LIST, ignored -> list(context)))))
                .then(track);
    }

    private int rootUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing track command.", "List tracks or choose one track to inspect or mutate.", CommandCatalogs.trackRootUsages());
    }

    private int list(CommandContext<S> context) throws CommandSyntaxException {
        Set<String> tracks = environment.trackService().getTracks();
        if (tracks.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.tracksEmpty());
            return Command.SINGLE_SUCCESS;
        }

        String rootLiteral = support.currentRootLiteral(context);
        List<CommandPaging.PagedRow> rows = tracks.stream().sorted(Comparator.naturalOrder())
                .map(track -> new CommandPaging.PagedRow(track, support.formatting().fullCommand(rootLiteral, "track " + track + " list"))).toList();
        support.paging().sendPagedRows(context, "Tracks", rows, "track list");
        return Command.SINGLE_SUCCESS;
    }

    private int targetUsage(CommandContext<S> context) {
        String track = StringArgumentType.getString(context, CommandArguments.TRACK);
        return support.sendUsage(context, "Missing track command.", "Choose what to do with track " + track + ".", CommandCatalogs.trackTargetUsages(track));
    }

    private int unknownTargetUsage(CommandContext<S> context) {
        String track = StringArgumentType.getString(context, CommandArguments.TRACK);
        return support.sendUsage(context, "Unknown track command: " + StringArgumentType.getString(context, CommandArguments.UNKNOWN),
                "Track commands manage ordered group ladders.", CommandCatalogs.trackTargetUsages(track));
    }

    private int create(CommandContext<S> context) throws CommandSyntaxException {
        String trackName = support.getTrackName(context);
        String normalizedTrackName = support.formatting().normalizeTrackName(trackName);
        if (environment.trackService().hasTrack(normalizedTrackName)) {
            environment.sendMessage(context.getSource(), CommandLang.trackAlreadyExists(normalizedTrackName));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().trackSnapshot(normalizedTrackName);
        try {
            environment.trackService().createTrack(trackName);
        } catch (RuntimeException exception) {
            throw support.trackOperationFailed(exception);
        }

        support.audit().recordAudit(context, "track.create", "track", normalizedTrackName, normalizedTrackName, beforeJson, support.audit().trackSnapshot(normalizedTrackName),
                true);
        environment.sendMessage(context.getSource(), CommandLang.trackCreated(normalizedTrackName));
        return Command.SINGLE_SUCCESS;
    }

    private int delete(CommandContext<S> context) throws CommandSyntaxException {
        String trackName = support.requireExistingTrack(context, support.getTrackName(context));
        if (!support.audit().confirmDestructiveCommand(context, support.audit().confirmationOperation("track-delete", trackName))) {
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().trackSnapshot(trackName);
        try {
            environment.trackService().deleteTrack(trackName);
        } catch (RuntimeException exception) {
            throw support.trackOperationFailed(exception);
        }

        support.audit().recordAudit(context, "track.delete", "track", trackName, trackName, beforeJson, support.audit().trackSnapshot(trackName), true);
        environment.sendMessage(context.getSource(), CommandLang.trackDeleted(trackName));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<S> renameCommand() {
        return LiteralArgumentBuilder.<S>literal("rename").requires(source -> support.canUse(source, PermissionNodes.ADMIN_TRACK_RENAME))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_RENAME, ignored -> renameUsage(context)))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.NEW_TRACK, StringArgumentType.word())
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_TRACK_RENAME, ignored -> rename(context))));
    }

    private int renameUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing new track name.", "Choose the new track name.",
                List.of("track " + StringArgumentType.getString(context, CommandArguments.TRACK) + " rename <new-track>"));
    }

    private int rename(CommandContext<S> context) throws CommandSyntaxException {
        String trackName = support.requireExistingTrack(context, support.getTrackName(context));
        String newTrackName = support.getNewTrackName(context);
        String normalizedNewTrackName = support.formatting().normalizeTrackName(newTrackName);
        if (support.formatting().normalizeTrackName(trackName).equals(normalizedNewTrackName)) {
            environment.sendMessage(context.getSource(), CommandLang.trackAlreadyNamed(support.formatting().normalizeTrackName(trackName), normalizedNewTrackName));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().renameTrackSnapshot(trackName, normalizedNewTrackName);
        try {
            environment.trackService().renameTrack(trackName, newTrackName);
        } catch (RuntimeException exception) {
            throw support.trackOperationFailed(exception);
        }

        support.audit().recordAudit(context, "track.rename", "track-rename", trackName + "->" + normalizedNewTrackName, trackName + " -> " + normalizedNewTrackName, beforeJson,
                support.audit().renameTrackSnapshot(trackName, normalizedNewTrackName), true);
        environment.sendMessage(context.getSource(), CommandLang.trackRenamed(trackName, normalizedNewTrackName));
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandContext<S> context) throws CommandSyntaxException {
        String trackName = support.requireExistingTrack(context, support.getTrackName(context));
        List<String> groups;
        try {
            groups = environment.trackService().getTrackGroups(trackName);
        } catch (RuntimeException exception) {
            throw support.trackOperationFailed(exception);
        }

        String rootLiteral = support.currentRootLiteral(context);
        List<CommandPaging.PagedRow> rows = new ArrayList<>();
        rows.add(new CommandPaging.PagedRow("name " + trackName, support.formatting().fullCommand(rootLiteral, "track " + trackName + " list")));
        rows.add(new CommandPaging.PagedRow("groups " + groups.size(), support.formatting().fullCommand(rootLiteral, "track " + trackName + " list")));
        rows.add(new CommandPaging.PagedRow("first " + (groups.isEmpty() ? "none" : groups.getFirst()),
                support.formatting().fullCommand(rootLiteral, "track " + trackName + " list")));
        rows.add(new CommandPaging.PagedRow("last " + (groups.isEmpty() ? "none" : groups.getLast()),
                support.formatting().fullCommand(rootLiteral, "track " + trackName + " list")));
        rows.add(new CommandPaging.PagedRow("ordered groups " + support.formatting().summarizeValues(groups),
                support.formatting().fullCommand(rootLiteral, "track " + trackName + " list")));
        support.paging().sendInfoRows(context, "Track " + trackName, rows);
        return Command.SINGLE_SUCCESS;
    }

    private int show(CommandContext<S> context) throws CommandSyntaxException {
        String trackName = support.requireExistingTrack(context, support.getTrackName(context));
        List<String> groups;
        try {
            groups = environment.trackService().getTrackGroups(trackName);
        } catch (RuntimeException exception) {
            throw support.trackOperationFailed(exception);
        }

        if (groups.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.trackGroupsEmpty(trackName));
            return Command.SINGLE_SUCCESS;
        }

        List<CommandPaging.PagedRow> rows = new ArrayList<>();
        String rootLiteral = support.currentRootLiteral(context);
        for (int index = 0; index < groups.size(); index++) {
            String groupName = groups.get(index);
            rows.add(new CommandPaging.PagedRow("#" + (index + 1) + " " + groupName, support.formatting().fullCommand(rootLiteral, "group " + groupName + " list")));
        }
        support.paging().sendPagedRows(context, "Track " + trackName, rows, "track " + StringArgumentType.getString(context, CommandArguments.TRACK) + " list");
        return Command.SINGLE_SUCCESS;
    }

    private int appendUsage(CommandContext<S> context) {
        String track = StringArgumentType.getString(context, CommandArguments.TRACK);
        return support.sendUsage(context, "Missing group name.", "Choose the group to append to this track.", List.of("track " + track + " append <group>"));
    }

    private int append(CommandContext<S> context) throws CommandSyntaxException {
        String trackName = support.requireExistingTrack(context, support.getTrackName(context));
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        List<String> updatedGroups = new ArrayList<>(environment.trackService().getTrackGroups(trackName));
        if (updatedGroups.contains(groupName)) {
            environment.sendMessage(context.getSource(), CommandLang.trackGroupAlreadyPresent(trackName, support.formatting().normalizeGroupName(groupName)));
            return Command.SINGLE_SUCCESS;
        }
        updatedGroups.add(groupName);
        return updateTrackGroups(context, trackName, updatedGroups, "track.group.append",
                CommandLang.trackGroupAppended(trackName, support.formatting().normalizeGroupName(groupName)));
    }

    private int insertUsage(CommandContext<S> context) {
        String track = StringArgumentType.getString(context, CommandArguments.TRACK);
        return support.sendUsage(context, "Missing track position or group name.", "Choose a 1-based position and a group to insert.",
                List.of("track " + track + " insert <position> <group>"));
    }

    private int insert(CommandContext<S> context) throws CommandSyntaxException {
        String trackName = support.requireExistingTrack(context, support.getTrackName(context));
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        List<String> updatedGroups = new ArrayList<>(environment.trackService().getTrackGroups(trackName));
        if (updatedGroups.contains(groupName)) {
            environment.sendMessage(context.getSource(), CommandLang.trackGroupAlreadyPresent(trackName, support.formatting().normalizeGroupName(groupName)));
            return Command.SINGLE_SUCCESS;
        }
        int position = support.getTrackPosition(context);
        if (position > updatedGroups.size() + 1) {
            throw support.trackOperationFailed(new IllegalArgumentException("track position out of range: " + position));
        }
        updatedGroups.add(position - 1, groupName);
        return updateTrackGroups(context, trackName, updatedGroups, "track.group.insert",
                CommandLang.trackGroupInserted(trackName, support.formatting().normalizeGroupName(groupName), position));
    }

    private int moveUsage(CommandContext<S> context) {
        String track = StringArgumentType.getString(context, CommandArguments.TRACK);
        return support.sendUsage(context, "Missing track group or new position.", "Choose an existing track group and a new 1-based position.",
                List.of("track " + track + " move <group> <position>"));
    }

    private int move(CommandContext<S> context) throws CommandSyntaxException {
        String trackName = support.requireExistingTrack(context, support.getTrackName(context));
        String groupName = support.requireTrackGroup(context, trackName, support.getGroupName(context));
        List<String> updatedGroups = new ArrayList<>(environment.trackService().getTrackGroups(trackName));
        int position = support.getTrackPosition(context);
        if (position > updatedGroups.size()) {
            throw support.trackOperationFailed(new IllegalArgumentException("track position out of range: " + position));
        }
        if (updatedGroups.indexOf(groupName) == position - 1) {
            environment.sendMessage(context.getSource(), CommandLang.trackGroupAlreadyPositioned(trackName, support.formatting().normalizeGroupName(groupName), position));
            return Command.SINGLE_SUCCESS;
        }
        updatedGroups.remove(groupName);
        updatedGroups.add(position - 1, groupName);
        return updateTrackGroups(context, trackName, updatedGroups, "track.group.move",
                CommandLang.trackGroupMoved(trackName, support.formatting().normalizeGroupName(groupName), position));
    }

    private int removeUsage(CommandContext<S> context) {
        String track = StringArgumentType.getString(context, CommandArguments.TRACK);
        return support.sendUsage(context, "Missing group name.", "Choose the group to remove from this track.", List.of("track " + track + " remove <group>"));
    }

    private int remove(CommandContext<S> context) throws CommandSyntaxException {
        String trackName = support.requireExistingTrack(context, support.getTrackName(context));
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        List<String> updatedGroups = new ArrayList<>(environment.trackService().getTrackGroups(trackName));
        if (!updatedGroups.contains(groupName)) {
            environment.sendMessage(context.getSource(), CommandLang.trackGroupAlreadyAbsent(trackName, support.formatting().normalizeGroupName(groupName)));
            return Command.SINGLE_SUCCESS;
        }
        updatedGroups.remove(groupName);
        return updateTrackGroups(context, trackName, updatedGroups, "track.group.remove",
                CommandLang.trackGroupRemoved(trackName, support.formatting().normalizeGroupName(groupName)));
    }

    private int updateTrackGroups(CommandContext<S> context, String trackName, List<String> updatedGroups, String action, CommandMessage successMessage)
            throws CommandSyntaxException {
        String beforeJson = support.audit().trackSnapshot(trackName);
        try {
            environment.trackService().setTrackGroups(trackName, updatedGroups);
        } catch (RuntimeException exception) {
            throw support.trackOperationFailed(exception);
        }
        support.audit().recordAudit(context, action, "track", trackName, trackName, beforeJson, support.audit().trackSnapshot(trackName), true);
        environment.sendMessage(context.getSource(), successMessage);
        return Command.SINGLE_SUCCESS;
    }

    private String[] trackPermissions() {
        return new String[]{PermissionNodes.ADMIN_TRACK_LIST, PermissionNodes.ADMIN_TRACK_INFO, PermissionNodes.ADMIN_TRACK_CREATE, PermissionNodes.ADMIN_TRACK_DELETE,
                PermissionNodes.ADMIN_TRACK_RENAME, PermissionNodes.ADMIN_TRACK_VIEW, PermissionNodes.ADMIN_TRACK_APPEND, PermissionNodes.ADMIN_TRACK_INSERT,
                PermissionNodes.ADMIN_TRACK_MOVE, PermissionNodes.ADMIN_TRACK_REMOVE};
    }

    private RequiredArgumentBuilder<S, String> trackGroupArgument() {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.GROUP, StringArgumentType.word()).suggests(this::suggestAvailableGroups);
    }

    private RequiredArgumentBuilder<S, String> existingTrackGroupArgument() {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.GROUP, StringArgumentType.word()).suggests(this::suggestTrackGroups);
    }

    private CompletableFuture<Suggestions> suggestTracks(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        environment.trackService().getTracks().stream().sorted(Comparator.naturalOrder()).filter(track -> track.startsWith(remaining)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestAvailableGroups(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
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

    private CompletableFuture<Suggestions> suggestTrackGroups(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        try {
            environment.trackService().getTrackGroups(StringArgumentType.getString(context, CommandArguments.TRACK)).stream().filter(group -> group.startsWith(remaining))
                    .forEach(builder::suggest);
        } catch (IllegalArgumentException ignored) {
            // Suggestions stay empty when the track is not yet known.
        }
        return builder.buildFuture();
    }
}
