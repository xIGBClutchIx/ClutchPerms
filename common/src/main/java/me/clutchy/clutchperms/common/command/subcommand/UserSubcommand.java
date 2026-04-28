package me.clutchy.clutchperms.common.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import me.clutchy.clutchperms.common.display.DisplayResolution;
import me.clutchy.clutchperms.common.display.DisplaySlot;
import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.permission.PermissionExplanation;
import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionResolution;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.subject.SubjectMetadata;

final class UserSubcommand<S> {

    private final CommandSupport<S> support;

    private final ClutchPermsCommandEnvironment<S> environment;

    UserSubcommand(CommandSupport<S> support) {
        this.support = support;
        this.environment = support.environment();
    }

    LiteralArgumentBuilder<S> builder() {
        RequiredArgumentBuilder<S, String> target = RequiredArgumentBuilder.<S, String>argument(CommandArguments.TARGET, StringArgumentType.word())
                .requires(source -> support.canUseAny(source, userPermissions()))
                .suggests((context, builder) -> support.targets().suggestUserTargets(context.getSource(), builder));

        return LiteralArgumentBuilder.<S>literal("user").requires(source -> support.canUseAny(source, userPermissions()))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_USER_LIST, ignored -> rootUsage(context)))
                .then(target.then(infoCommand()).then(listCommand()).then(getCommand()).then(setCommand()).then(clearCommand()).then(clearAllCommand()).then(groupsCommand())
                        .then(tracksCommand()).then(displayCommand("prefix", DisplaySlot.PREFIX)).then(displayCommand("suffix", DisplaySlot.SUFFIX)).then(groupCommand())
                        .then(trackCommand()).then(checkCommand()).then(explainCommand())
                        .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_LIST, (source, subject) -> targetUsage(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.UNKNOWN, StringArgumentType.word())
                                .requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_LIST)).executes(context -> support.executeAuthorizedForSubject(context,
                                        PermissionNodes.ADMIN_USER_LIST, (source, subject) -> unknownTargetUsage(context)))));
    }

    int mutateOpGroupMembership(CommandContext<S> context, CommandSubject subject, boolean addMembership) throws CommandSyntaxException {
        boolean hasOpGroup = environment.groupService().getSubjectGroups(subject.id()).contains(GroupService.OP_GROUP);
        if (addMembership == hasOpGroup) {
            environment.sendMessage(context.getSource(),
                    addMembership
                            ? CommandLang.userGroupAlreadyAdded(support.formatting().formatSubject(subject), GroupService.OP_GROUP)
                            : CommandLang.userGroupAlreadyRemoved(support.formatting().formatSubject(subject), GroupService.OP_GROUP));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().subjectMembershipSnapshot(subject.id());
        try {
            if (addMembership) {
                environment.groupService().addSubjectGroup(subject.id(), GroupService.OP_GROUP);
            } else {
                environment.groupService().removeSubjectGroup(subject.id(), GroupService.OP_GROUP);
            }
        } catch (RuntimeException exception) {
            throw support.groupOperationFailed(exception);
        }

        environment.refreshRuntimePermissions();
        support.audit().recordAudit(context, addMembership ? "user.group.add" : "user.group.remove", "user-groups", subject.id().toString(),
                support.formatting().formatSubject(subject), beforeJson, support.audit().subjectMembershipSnapshot(subject.id()), true);
        environment.sendMessage(context.getSource(),
                addMembership
                        ? CommandLang.userGroupAdded(support.formatting().formatSubject(subject), GroupService.OP_GROUP)
                        : CommandLang.userGroupRemoved(support.formatting().formatSubject(subject), GroupService.OP_GROUP));
        return Command.SINGLE_SUCCESS;
    }

    int opShortcutUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing user target.", "Provide an exact online name, resolvable offline name, stored last-known name, or UUID.", List.of("<target>"));
    }

    private int rootUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing user target.", "Provide an exact online name, resolvable offline name, stored last-known name, or UUID.",
                CommandCatalogs.userRootUsages());
    }

    private int targetUsage(CommandContext<S> context) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        return support.sendUsage(context, "Missing user command.", "Choose what to do with " + target + ".", CommandCatalogs.userTargetUsages(target));
    }

    private int unknownTargetUsage(CommandContext<S> context) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        return support.sendUsage(context, "Unknown user command: " + StringArgumentType.getString(context, CommandArguments.UNKNOWN),
                "User commands inspect or mutate direct permissions and group membership.", CommandCatalogs.userTargetUsages(target));
    }

    private LiteralArgumentBuilder<S> infoCommand() {
        return LiteralArgumentBuilder.<S>literal("info").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_INFO))
                .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_INFO, (source, subject) -> info(context, subject)));
    }

    private int info(CommandContext<S> context, CommandSubject subject) {
        String rootLiteral = support.currentRootLiteral(context);
        String subjectListCommand = support.formatting().fullCommand(rootLiteral, "user " + subject.id() + " list");
        String subjectGroupsCommand = support.formatting().fullCommand(rootLiteral, "user " + subject.id() + " groups");
        String subjectTracksCommand = support.formatting().fullCommand(rootLiteral, "user " + subject.id() + " tracks");
        String subjectPrefixCommand = support.formatting().fullCommand(rootLiteral, "user " + subject.id() + " prefix get");
        String subjectSuffixCommand = support.formatting().fullCommand(rootLiteral, "user " + subject.id() + " suffix get");

        List<CommandPaging.PagedRow> rows = new ArrayList<>();
        rows.add(new CommandPaging.PagedRow("subject " + support.formatting().formatSubject(subject), subjectListCommand));
        Optional<SubjectMetadata> metadata = environment.subjectMetadataService().getSubject(subject.id());
        rows.add(new CommandPaging.PagedRow(
                metadata.map(value -> "stored last-known name " + value.lastKnownName() + ", last seen " + value.lastSeen()).orElse("stored metadata none"), subjectListCommand));
        rows.add(new CommandPaging.PagedRow("direct permissions " + environment.permissionService().getPermissions(subject.id()).size(), subjectListCommand));
        rows.add(new CommandPaging.PagedRow("effective permissions " + environment.permissionResolver().getEffectivePermissions(subject.id()).size(), subjectListCommand));
        rows.add(new CommandPaging.PagedRow("groups " + support.formatting().summarizeSubjectGroups(subject.id()), subjectGroupsCommand));
        rows.add(new CommandPaging.PagedRow("tracks " + support.formatting().summarizeSubjectTracks(subject.id()), subjectTracksCommand));
        rows.add(new CommandPaging.PagedRow("direct prefix " + support.formatting().formatDisplayValue(support.formatting().subjectDisplayValue(subject.id(), DisplaySlot.PREFIX)),
                subjectPrefixCommand));
        rows.add(new CommandPaging.PagedRow(
                "effective prefix " + support.formatting().formatEffectiveDisplay(environment.displayResolver().resolve(subject.id(), DisplaySlot.PREFIX)), subjectPrefixCommand));
        rows.add(new CommandPaging.PagedRow("direct suffix " + support.formatting().formatDisplayValue(support.formatting().subjectDisplayValue(subject.id(), DisplaySlot.SUFFIX)),
                subjectSuffixCommand));
        rows.add(new CommandPaging.PagedRow(
                "effective suffix " + support.formatting().formatEffectiveDisplay(environment.displayResolver().resolve(subject.id(), DisplaySlot.SUFFIX)), subjectSuffixCommand));
        support.paging().sendInfoRows(context, "User " + support.formatting().formatSubject(subject), rows);
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<S> listCommand() {
        return LiteralArgumentBuilder.<S>literal("list").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_LIST))
                .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_LIST, (source, subject) -> list(context, subject)))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.PAGE, StringArgumentType.word())
                        .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_LIST, (source, subject) -> list(context, subject))));
    }

    private int list(CommandContext<S> context, CommandSubject subject) throws CommandSyntaxException {
        Map<String, PermissionValue> permissions = environment.permissionService().getPermissions(subject.id());
        String rootLiteral = support.currentRootLiteral(context);
        List<CommandPaging.PagedRow> rows = new ArrayList<>();
        support.formatting().addSubjectDisplayRows(rows, rootLiteral, subject);
        permissions.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> new CommandPaging.PagedRow(entry.getKey() + "=" + entry.getValue().name(),
                support.formatting().fullCommand(rootLiteral, "user " + subject.id() + " get " + entry.getKey()))).forEach(rows::add);
        if (rows.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.permissionsEmpty(support.formatting().formatSubject(subject)));
            return Command.SINGLE_SUCCESS;
        }
        support.paging().sendPagedRows(context, "Permissions for " + support.formatting().formatSubject(subject), rows,
                "user " + StringArgumentType.getString(context, CommandArguments.TARGET) + " list");
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<S> getCommand() {
        return LiteralArgumentBuilder.<S>literal("get").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_GET))
                .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_GET, (source, subject) -> getUsage(context)))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.NODE, StringArgumentType.greedyString()).suggests(support::suggestPermissionNodes)
                        .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_GET, (source, subject) -> get(context, subject))));
    }

    private int getUsage(CommandContext<S> context) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        return support.sendUsage(context, "Missing permission node.", "Choose the direct user permission node to read.", List.of("user " + target + " get <node>"));
    }

    private int get(CommandContext<S> context, CommandSubject subject) throws CommandSyntaxException {
        String node = support.getNode(context);
        PermissionValue value = environment.permissionService().getPermission(subject.id(), node);
        environment.sendMessage(context.getSource(), CommandLang.permissionGet(support.formatting().formatSubject(subject), node, value));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<S> setCommand() {
        return LiteralArgumentBuilder.<S>literal("set").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_SET))
                .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_SET, (source, subject) -> setUsage(context)))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.ASSIGNMENT, StringArgumentType.greedyString()).suggests(support::suggestPermissionAssignment)
                        .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_SET, (source, subject) -> set(context, subject))));
    }

    private int setUsage(CommandContext<S> context) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        return support.sendUsage(context, "Missing permission assignment.", "Set a node to true or false.", List.of("user " + target + " set <node> <true|false>"));
    }

    private int set(CommandContext<S> context, CommandSubject subject) throws CommandSyntaxException {
        CommandSupport.PermissionAssignment assignment = support.getPermissionAssignment(context);
        if (environment.permissionService().getPermission(subject.id(), assignment.node()) == assignment.value()) {
            environment.sendMessage(context.getSource(), CommandLang.permissionAlreadySet(assignment.node(), support.formatting().formatSubject(subject), assignment.value()));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().subjectPermissionsSnapshot(subject.id());
        try {
            environment.permissionService().setPermission(subject.id(), assignment.node(), assignment.value());
        } catch (RuntimeException exception) {
            throw support.permissionOperationFailed(exception);
        }
        support.audit().recordAudit(context, "user.permission.set", "user-permissions", subject.id().toString(), support.formatting().formatSubject(subject), beforeJson,
                support.audit().subjectPermissionsSnapshot(subject.id()), true);
        environment.sendMessage(context.getSource(), CommandLang.permissionSet(assignment.node(), support.formatting().formatSubject(subject), assignment.value()));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<S> clearCommand() {
        return LiteralArgumentBuilder.<S>literal("clear").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_CLEAR))
                .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_CLEAR, (source, subject) -> clearUsage(context)))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.NODE, StringArgumentType.greedyString()).suggests(support::suggestPermissionNodes)
                        .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_CLEAR, (source, subject) -> clear(context, subject))));
    }

    private int clearUsage(CommandContext<S> context) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        return support.sendUsage(context, "Missing permission node.", "Choose the direct user permission node to clear.", List.of("user " + target + " clear <node>"));
    }

    private int clear(CommandContext<S> context, CommandSubject subject) throws CommandSyntaxException {
        String node = support.getNode(context);
        if (environment.permissionService().getPermission(subject.id(), node) == PermissionValue.UNSET) {
            environment.sendMessage(context.getSource(), CommandLang.permissionAlreadyClear(node, support.formatting().formatSubject(subject)));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().subjectPermissionsSnapshot(subject.id());
        try {
            environment.permissionService().clearPermission(subject.id(), node);
        } catch (RuntimeException exception) {
            throw support.permissionOperationFailed(exception);
        }
        support.audit().recordAudit(context, "user.permission.clear", "user-permissions", subject.id().toString(), support.formatting().formatSubject(subject), beforeJson,
                support.audit().subjectPermissionsSnapshot(subject.id()), true);
        environment.sendMessage(context.getSource(), CommandLang.permissionClear(node, support.formatting().formatSubject(subject)));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<S> clearAllCommand() {
        return LiteralArgumentBuilder.<S>literal("clear-all").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_CLEAR_ALL))
                .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_CLEAR_ALL, (source, subject) -> clearAll(context, subject)));
    }

    private int clearAll(CommandContext<S> context, CommandSubject subject) throws CommandSyntaxException {
        if (environment.permissionService().getPermissions(subject.id()).isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.permissionsEmpty(support.formatting().formatSubject(subject)));
            return Command.SINGLE_SUCCESS;
        }
        if (!support.audit().confirmDestructiveCommand(context, support.audit().confirmationOperation("user-clear-all", subject.id().toString()))) {
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().subjectPermissionsSnapshot(subject.id());
        int removedPermissions;
        try {
            removedPermissions = environment.permissionService().clearPermissions(subject.id());
        } catch (RuntimeException exception) {
            throw support.permissionOperationFailed(exception);
        }

        if (removedPermissions == 0) {
            environment.sendMessage(context.getSource(), CommandLang.permissionsEmpty(support.formatting().formatSubject(subject)));
        } else {
            support.audit().recordAudit(context, "user.permission.clear-all", "user-permissions", subject.id().toString(), support.formatting().formatSubject(subject), beforeJson,
                    support.audit().subjectPermissionsSnapshot(subject.id()), true);
            environment.sendMessage(context.getSource(), CommandLang.permissionsClearAll(support.formatting().formatSubject(subject), removedPermissions));
        }
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<S> groupsCommand() {
        return LiteralArgumentBuilder.<S>literal("groups").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_GROUPS))
                .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_GROUPS, (source, subject) -> groups(context, subject)))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.PAGE, StringArgumentType.word())
                        .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_GROUPS, (source, subject) -> groups(context, subject))));
    }

    private int groups(CommandContext<S> context, CommandSubject subject) throws CommandSyntaxException {
        Set<String> groups = new LinkedHashSet<>(environment.groupService().getSubjectGroups(subject.id()));
        if (environment.groupService().hasGroup(GroupService.DEFAULT_GROUP)) {
            groups.add(GroupService.DEFAULT_GROUP + " (implicit)");
        }

        if (groups.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.userGroupsEmpty(support.formatting().formatSubject(subject)));
            return Command.SINGLE_SUCCESS;
        }

        String rootLiteral = support.currentRootLiteral(context);
        List<CommandPaging.PagedRow> rows = groups.stream().map(group -> {
            String groupName = group.endsWith(" (implicit)") ? group.substring(0, group.indexOf(" (implicit)")) : group;
            return new CommandPaging.PagedRow(group, support.formatting().fullCommand(rootLiteral, "group " + groupName + " list"));
        }).toList();
        support.paging().sendPagedRows(context, "Groups for " + support.formatting().formatSubject(subject), rows,
                "user " + StringArgumentType.getString(context, CommandArguments.TARGET) + " groups");
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<S> tracksCommand() {
        return LiteralArgumentBuilder.<S>literal("tracks").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_TRACK_LIST))
                .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_TRACK_LIST, (source, subject) -> tracks(context, subject)))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.PAGE, StringArgumentType.word())
                        .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_TRACK_LIST, (source, subject) -> tracks(context, subject))));
    }

    private int tracks(CommandContext<S> context, CommandSubject subject) throws CommandSyntaxException {
        String rootLiteral = support.currentRootLiteral(context);
        List<CommandPaging.PagedRow> rows = environment.trackService().getTracks().stream().sorted(Comparator.naturalOrder()).map(trackName -> {
            CommandFormatting.TrackSubjectState state = support.formatting().subjectTrackState(subject.id(), trackName);
            if (!state.hasPosition() && !state.hasConflict()) {
                return null;
            }
            return new CommandPaging.PagedRow(trackName + ": " + support.formatting().formatTrackSubjectState(state),
                    support.formatting().fullCommand(rootLiteral, "track " + trackName + " list"));
        }).filter(Objects::nonNull).toList();
        if (rows.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.userTracksEmpty(support.formatting().formatSubject(subject)));
            return Command.SINGLE_SUCCESS;
        }
        support.paging().sendPagedRows(context, "Tracks for " + support.formatting().formatSubject(subject), rows,
                "user " + StringArgumentType.getString(context, CommandArguments.TARGET) + " tracks");
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<S> displayCommand(String literal, DisplaySlot slot) {
        return LiteralArgumentBuilder.<S>literal(literal)
                .requires(source -> support.canUseAny(source, PermissionNodes.ADMIN_USER_DISPLAY_VIEW, PermissionNodes.ADMIN_USER_DISPLAY_SET,
                        PermissionNodes.ADMIN_USER_DISPLAY_CLEAR))
                .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_DISPLAY_VIEW, (source, subject) -> displayUsage(context, slot)))
                .then(LiteralArgumentBuilder.<S>literal("get").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_DISPLAY_VIEW)).executes(
                        context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_DISPLAY_VIEW, (source, subject) -> displayGet(context, subject, slot))))
                .then(LiteralArgumentBuilder.<S>literal("set").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_DISPLAY_SET)).executes(
                        context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_DISPLAY_SET, (source, subject) -> displaySetUsage(context, slot)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.DISPLAY_VALUE, StringArgumentType.greedyString())
                                .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_DISPLAY_SET,
                                        (source, subject) -> displaySet(context, subject, slot)))))
                .then(LiteralArgumentBuilder.<S>literal("clear").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_DISPLAY_CLEAR)).executes(context -> support
                        .executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_DISPLAY_CLEAR, (source, subject) -> displayClear(context, subject, slot))));
    }

    private int displayUsage(CommandContext<S> context, DisplaySlot slot) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        return support.sendUsage(context, "Missing user " + slot.label() + " command.", "Get, set, or clear this user's direct " + slot.label() + ".",
                List.of("user " + target + " " + slot.label() + " get", "user " + target + " " + slot.label() + " set <text>", "user " + target + " " + slot.label() + " clear"));
    }

    private int displaySetUsage(CommandContext<S> context, DisplaySlot slot) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        return support.sendUsage(context, "Missing display text.", "Use ampersand formatting codes like &7, &a, &l, &o, &r, and && for a literal ampersand.",
                List.of("user " + target + " " + slot.label() + " set <text>"));
    }

    private int displayGet(CommandContext<S> context, CommandSubject subject, DisplaySlot slot) {
        Optional<DisplayText> directValue = support.formatting().subjectDisplayValue(subject.id(), slot);
        if (directValue.isPresent()) {
            environment.sendMessage(context.getSource(), CommandLang.userDisplayDirect(support.formatting().formatSubject(subject), slot.label(), directValue.get().rawText()));
        } else {
            environment.sendMessage(context.getSource(), CommandLang.userDisplayDirectUnset(support.formatting().formatSubject(subject), slot.label()));
        }
        DisplayResolution resolution = environment.displayResolver().resolve(subject.id(), slot);
        if (resolution.value().isPresent()) {
            environment.sendMessage(context.getSource(), CommandLang.userDisplayEffective(support.formatting().formatSubject(subject), slot.label(),
                    resolution.value().get().rawText(), support.formatting().formatDisplaySource(resolution)));
        } else {
            environment.sendMessage(context.getSource(), CommandLang.userDisplayEffectiveUnset(support.formatting().formatSubject(subject), slot.label()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int displaySet(CommandContext<S> context, CommandSubject subject, DisplaySlot slot) throws CommandSyntaxException {
        DisplayText displayText = support.getDisplayText(context);
        if (Optional.of(displayText).equals(support.formatting().subjectDisplayValue(subject.id(), slot))) {
            environment.sendMessage(context.getSource(), CommandLang.userDisplayAlreadySet(slot.label(), support.formatting().formatSubject(subject), displayText.rawText()));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().subjectDisplaySnapshot(subject.id());
        try {
            if (slot == DisplaySlot.PREFIX) {
                environment.subjectMetadataService().setSubjectPrefix(subject.id(), displayText);
            } else {
                environment.subjectMetadataService().setSubjectSuffix(subject.id(), displayText);
            }
        } catch (RuntimeException exception) {
            throw support.displayOperationFailed(exception);
        }
        support.audit().recordAudit(context, "user.display." + slot.label() + ".set", "user-display", subject.id().toString(), support.formatting().formatSubject(subject),
                beforeJson, support.audit().subjectDisplaySnapshot(subject.id()), true);
        environment.sendMessage(context.getSource(), CommandLang.userDisplaySet(slot.label(), support.formatting().formatSubject(subject), displayText.rawText()));
        return Command.SINGLE_SUCCESS;
    }

    private int displayClear(CommandContext<S> context, CommandSubject subject, DisplaySlot slot) throws CommandSyntaxException {
        if (support.formatting().subjectDisplayValue(subject.id(), slot).isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.userDisplayAlreadyClear(slot.label(), support.formatting().formatSubject(subject)));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().subjectDisplaySnapshot(subject.id());
        try {
            if (slot == DisplaySlot.PREFIX) {
                environment.subjectMetadataService().clearSubjectPrefix(subject.id());
            } else {
                environment.subjectMetadataService().clearSubjectSuffix(subject.id());
            }
        } catch (RuntimeException exception) {
            throw support.displayOperationFailed(exception);
        }
        support.audit().recordAudit(context, "user.display." + slot.label() + ".clear", "user-display", subject.id().toString(), support.formatting().formatSubject(subject),
                beforeJson, support.audit().subjectDisplaySnapshot(subject.id()), true);
        environment.sendMessage(context.getSource(), CommandLang.userDisplayClear(slot.label(), support.formatting().formatSubject(subject)));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<S> groupCommand() {
        return LiteralArgumentBuilder.<S>literal("group")
                .requires(source -> support.canUseAny(source, PermissionNodes.ADMIN_USER_GROUPS, PermissionNodes.ADMIN_USER_GROUP_ADD, PermissionNodes.ADMIN_USER_GROUP_REMOVE))
                .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_GROUPS, (source, subject) -> groupUsage(context)))
                .then(LiteralArgumentBuilder.<S>literal("add").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_GROUP_ADD))
                        .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_GROUP_ADD, (source, subject) -> groupAddUsage(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.GROUP, StringArgumentType.word()).suggests(this::suggestAddGroups).executes(
                                context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_GROUP_ADD, (source, subject) -> groupAdd(context, subject)))))
                .then(LiteralArgumentBuilder.<S>literal("remove").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_GROUP_REMOVE))
                        .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_GROUP_REMOVE, (source, subject) -> groupRemoveUsage(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.GROUP, StringArgumentType.word()).suggests(this::suggestRemoveGroups)
                                .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_GROUP_REMOVE,
                                        (source, subject) -> groupRemove(context, subject)))))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.UNKNOWN, StringArgumentType.word())
                        .requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_GROUPS))
                        .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_GROUPS, (source, subject) -> unknownGroupUsage(context))));
    }

    private int groupUsage(CommandContext<S> context) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        return support.sendUsage(context, "Missing user group command.", "Add or remove explicit group membership.",
                List.of("user " + target + " group add <group>", "user " + target + " group remove <group>"));
    }

    private int groupAddUsage(CommandContext<S> context) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        return support.sendUsage(context, "Missing group name.", "Choose the group to add this user to.", List.of("user " + target + " group add <group>"));
    }

    private int groupAdd(CommandContext<S> context, CommandSubject subject) throws CommandSyntaxException {
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        if (environment.groupService().getSubjectGroups(subject.id()).contains(groupName)) {
            environment.sendMessage(context.getSource(),
                    CommandLang.userGroupAlreadyAdded(support.formatting().formatSubject(subject), support.formatting().normalizeGroupName(groupName)));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().subjectMembershipSnapshot(subject.id());
        try {
            environment.groupService().addSubjectGroup(subject.id(), groupName);
        } catch (RuntimeException exception) {
            throw support.groupOperationFailed(exception);
        }

        support.audit().recordAudit(context, "user.group.add", "user-groups", subject.id().toString(), support.formatting().formatSubject(subject), beforeJson,
                support.audit().subjectMembershipSnapshot(subject.id()), true);
        environment.sendMessage(context.getSource(), CommandLang.userGroupAdded(support.formatting().formatSubject(subject), support.formatting().normalizeGroupName(groupName)));
        return Command.SINGLE_SUCCESS;
    }

    private int groupRemoveUsage(CommandContext<S> context) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        return support.sendUsage(context, "Missing group name.", "Choose the group to remove this user from.", List.of("user " + target + " group remove <group>"));
    }

    private int groupRemove(CommandContext<S> context, CommandSubject subject) throws CommandSyntaxException {
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        if (!environment.groupService().getSubjectGroups(subject.id()).contains(groupName)) {
            environment.sendMessage(context.getSource(),
                    CommandLang.userGroupAlreadyRemoved(support.formatting().formatSubject(subject), support.formatting().normalizeGroupName(groupName)));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().subjectMembershipSnapshot(subject.id());
        try {
            environment.groupService().removeSubjectGroup(subject.id(), groupName);
        } catch (RuntimeException exception) {
            throw support.groupOperationFailed(exception);
        }

        support.audit().recordAudit(context, "user.group.remove", "user-groups", subject.id().toString(), support.formatting().formatSubject(subject), beforeJson,
                support.audit().subjectMembershipSnapshot(subject.id()), true);
        environment.sendMessage(context.getSource(), CommandLang.userGroupRemoved(support.formatting().formatSubject(subject), support.formatting().normalizeGroupName(groupName)));
        return Command.SINGLE_SUCCESS;
    }

    private int unknownGroupUsage(CommandContext<S> context) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        return support.sendUsage(context, "Unknown user group command: " + StringArgumentType.getString(context, CommandArguments.UNKNOWN),
                "User group commands add or remove explicit memberships.", List.of("user " + target + " group add <group>", "user " + target + " group remove <group>"));
    }

    private LiteralArgumentBuilder<S> trackCommand() {
        return LiteralArgumentBuilder.<S>literal("track")
                .requires(source -> support.canUseAny(source, PermissionNodes.ADMIN_USER_TRACK_LIST, PermissionNodes.ADMIN_USER_TRACK_PROMOTE,
                        PermissionNodes.ADMIN_USER_TRACK_DEMOTE))
                .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_TRACK_LIST, (source, subject) -> trackUsage(context)))
                .then(LiteralArgumentBuilder.<S>literal("promote").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_TRACK_PROMOTE))
                        .executes(
                                context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_TRACK_PROMOTE, (source, subject) -> trackPromoteUsage(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.TRACK, StringArgumentType.word()).suggests(this::suggestTracks)
                                .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_TRACK_PROMOTE,
                                        (source, subject) -> trackPromote(context, subject)))))
                .then(LiteralArgumentBuilder.<S>literal("demote").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_TRACK_DEMOTE))
                        .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_TRACK_DEMOTE, (source, subject) -> trackDemoteUsage(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.TRACK, StringArgumentType.word()).suggests(this::suggestTracks)
                                .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_TRACK_DEMOTE,
                                        (source, subject) -> trackDemote(context, subject)))))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.UNKNOWN, StringArgumentType.word())
                        .requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_TRACK_LIST))
                        .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_TRACK_LIST, (source, subject) -> unknownTrackUsage(context))));
    }

    private int trackUsage(CommandContext<S> context) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        return support.sendUsage(context, "Missing user track command.", "List, promote, or demote this user's track position.",
                List.of("user " + target + " track promote <track>", "user " + target + " track demote <track>"));
    }

    private int trackPromoteUsage(CommandContext<S> context) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        return support.sendUsage(context, "Missing track name.", "Choose the track to promote this user on.", List.of("user " + target + " track promote <track>"));
    }

    private int trackPromote(CommandContext<S> context, CommandSubject subject) throws CommandSyntaxException {
        String trackName = support.requireExistingTrack(context, support.getTrackName(context));
        CommandFormatting.TrackSubjectState state = support.formatting().subjectTrackState(subject.id(), trackName);
        if (state.hasConflict()) {
            throw support.trackOperationFailed(
                    new IllegalArgumentException("user matches multiple explicit groups on track " + trackName + ": " + String.join(", ", state.explicitMatches())));
        }
        if (state.trackGroups().isEmpty()) {
            throw support.trackOperationFailed(new IllegalArgumentException("track has no groups: " + trackName));
        }

        Set<String> updatedGroups = new LinkedHashSet<>(environment.groupService().getSubjectGroups(subject.id()));
        String targetGroup;
        if (state.hasExplicitMatch()) {
            int currentIndex = state.currentIndex();
            if (currentIndex >= state.trackGroups().size() - 1) {
                throw support.trackOperationFailed(new IllegalArgumentException("user is already at the end of track " + trackName));
            }
            updatedGroups.remove(state.currentGroup());
            targetGroup = state.trackGroups().get(currentIndex + 1);
            updatedGroups.add(targetGroup);
        } else if (state.implicitDefault()) {
            if (state.trackGroups().size() < 2) {
                throw support.trackOperationFailed(new IllegalArgumentException("user is already at the end of track " + trackName));
            }
            targetGroup = state.trackGroups().get(1);
            updatedGroups.add(targetGroup);
        } else {
            targetGroup = state.trackGroups().getFirst();
            updatedGroups.add(targetGroup);
        }

        String beforeJson = support.audit().subjectMembershipSnapshot(subject.id());
        try {
            environment.groupService().setSubjectGroups(subject.id(), updatedGroups);
        } catch (RuntimeException exception) {
            throw support.trackOperationFailed(exception);
        }
        support.audit().recordAudit(context, "user.track.promote", "user-groups", subject.id().toString(), support.formatting().formatSubject(subject), beforeJson,
                support.audit().subjectMembershipSnapshot(subject.id()), true);
        environment.sendMessage(context.getSource(), CommandLang.userTrackPromoted(support.formatting().formatSubject(subject), trackName, targetGroup));
        return Command.SINGLE_SUCCESS;
    }

    private int trackDemoteUsage(CommandContext<S> context) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        return support.sendUsage(context, "Missing track name.", "Choose the track to demote this user on.", List.of("user " + target + " track demote <track>"));
    }

    private int trackDemote(CommandContext<S> context, CommandSubject subject) throws CommandSyntaxException {
        String trackName = support.requireExistingTrack(context, support.getTrackName(context));
        CommandFormatting.TrackSubjectState state = support.formatting().subjectTrackState(subject.id(), trackName);
        if (state.hasConflict()) {
            throw support.trackOperationFailed(
                    new IllegalArgumentException("user matches multiple explicit groups on track " + trackName + ": " + String.join(", ", state.explicitMatches())));
        }
        if (state.trackGroups().isEmpty()) {
            throw support.trackOperationFailed(new IllegalArgumentException("track has no groups: " + trackName));
        }
        if (state.implicitDefault()) {
            throw support.trackOperationFailed(new IllegalArgumentException("user is already at the start of track " + trackName));
        }
        if (!state.hasExplicitMatch()) {
            throw support.trackOperationFailed(new IllegalArgumentException("user is not on track " + trackName));
        }

        int currentIndex = state.currentIndex();
        if (currentIndex == 0) {
            throw support.trackOperationFailed(new IllegalArgumentException("user is already at the start of track " + trackName));
        }

        Set<String> updatedGroups = new LinkedHashSet<>(environment.groupService().getSubjectGroups(subject.id()));
        updatedGroups.remove(state.currentGroup());
        String targetGroup = state.trackGroups().get(currentIndex - 1);
        if (!GroupService.DEFAULT_GROUP.equals(targetGroup)) {
            updatedGroups.add(targetGroup);
        }

        String beforeJson = support.audit().subjectMembershipSnapshot(subject.id());
        try {
            environment.groupService().setSubjectGroups(subject.id(), updatedGroups);
        } catch (RuntimeException exception) {
            throw support.trackOperationFailed(exception);
        }
        support.audit().recordAudit(context, "user.track.demote", "user-groups", subject.id().toString(), support.formatting().formatSubject(subject), beforeJson,
                support.audit().subjectMembershipSnapshot(subject.id()), true);
        environment.sendMessage(context.getSource(), CommandLang.userTrackDemoted(support.formatting().formatSubject(subject), trackName, targetGroup));
        return Command.SINGLE_SUCCESS;
    }

    private int unknownTrackUsage(CommandContext<S> context) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        return support.sendUsage(context, "Unknown user track command: " + StringArgumentType.getString(context, CommandArguments.UNKNOWN),
                "User track commands list, promote, or demote a user's track position.",
                List.of("user " + target + " tracks", "user " + target + " track promote <track>", "user " + target + " track demote <track>"));
    }

    private LiteralArgumentBuilder<S> checkCommand() {
        return LiteralArgumentBuilder.<S>literal("check").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_CHECK))
                .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_CHECK, (source, subject) -> checkUsage(context)))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.NODE, StringArgumentType.greedyString()).suggests(support::suggestPermissionNodes)
                        .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_CHECK, (source, subject) -> check(context, subject))));
    }

    private int checkUsage(CommandContext<S> context) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        return support.sendUsage(context, "Missing permission node.", "Choose the effective permission node to check.", List.of("user " + target + " check <node>"));
    }

    private int check(CommandContext<S> context, CommandSubject subject) throws CommandSyntaxException {
        String node = support.getNode(context);
        PermissionResolution resolution = environment.permissionResolver().resolve(subject.id(), node);
        String source = support.formatting().formatResolutionSource(resolution);
        String assignmentNode = resolution.assignmentNode();
        if (assignmentNode != null && !assignmentNode.equals(PermissionNodes.normalize(node))) {
            environment.sendMessage(context.getSource(),
                    CommandLang.permissionCheck(support.formatting().formatSubject(subject), node, resolution.value(), source, assignmentNode));
        } else {
            environment.sendMessage(context.getSource(), CommandLang.permissionCheck(support.formatting().formatSubject(subject), node, resolution.value(), source));
        }
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<S> explainCommand() {
        return LiteralArgumentBuilder.<S>literal("explain").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USER_EXPLAIN))
                .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_EXPLAIN, (source, subject) -> explainUsage(context)))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.NODE, StringArgumentType.greedyString()).suggests(support::suggestPermissionNodes)
                        .executes(context -> support.executeAuthorizedForSubject(context, PermissionNodes.ADMIN_USER_EXPLAIN, (source, subject) -> explain(context, subject))));
    }

    private int explainUsage(CommandContext<S> context) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        return support.sendUsage(context, "Missing permission node.", "Choose the effective permission node to explain.", List.of("user " + target + " explain <node>"));
    }

    private int explain(CommandContext<S> context, CommandSubject subject) throws CommandSyntaxException {
        String node = support.getNode(context);
        PermissionExplanation explanation = environment.permissionResolver().explain(subject.id(), node);
        PermissionResolution resolution = explanation.resolution();

        environment.sendMessage(context.getSource(), CommandLang.permissionExplainHeader(support.formatting().formatSubject(subject), explanation.node()));
        if (resolution.value() == PermissionValue.UNSET) {
            environment.sendMessage(context.getSource(), CommandLang.permissionExplainResultUnset());
        } else {
            String source = support.formatting().formatResolutionSource(resolution);
            String assignmentNode = resolution.assignmentNode();
            if (assignmentNode != null && !assignmentNode.equals(explanation.node())) {
                environment.sendMessage(context.getSource(), CommandLang.permissionExplainResult(resolution.value(), source, assignmentNode));
            } else {
                environment.sendMessage(context.getSource(), CommandLang.permissionExplainResult(resolution.value(), source));
            }
        }
        environment.sendMessage(context.getSource(), CommandLang.permissionExplainOrder());
        if (explanation.matches().isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.permissionExplainNoMatches());
        } else {
            explanation.matches().forEach(match -> environment.sendMessage(context.getSource(),
                    CommandLang.permissionExplainMatch(support.formatting().formatExplanationSource(match), match.assignmentNode(), match.value(), match.winning())));
        }
        return Command.SINGLE_SUCCESS;
    }

    private String[] userPermissions() {
        return new String[]{PermissionNodes.ADMIN_USER_INFO, PermissionNodes.ADMIN_USER_LIST, PermissionNodes.ADMIN_USER_GET, PermissionNodes.ADMIN_USER_SET,
                PermissionNodes.ADMIN_USER_CLEAR, PermissionNodes.ADMIN_USER_CLEAR_ALL, PermissionNodes.ADMIN_USER_CHECK, PermissionNodes.ADMIN_USER_EXPLAIN,
                PermissionNodes.ADMIN_USER_GROUPS, PermissionNodes.ADMIN_USER_GROUP_ADD, PermissionNodes.ADMIN_USER_GROUP_REMOVE, PermissionNodes.ADMIN_USER_DISPLAY_VIEW,
                PermissionNodes.ADMIN_USER_TRACK_LIST, PermissionNodes.ADMIN_USER_TRACK_PROMOTE, PermissionNodes.ADMIN_USER_TRACK_DEMOTE, PermissionNodes.ADMIN_USER_DISPLAY_SET,
                PermissionNodes.ADMIN_USER_DISPLAY_CLEAR};
    }

    private CompletableFuture<Suggestions> suggestAddGroups(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        Set<String> assignedGroups = support.targets().resolveSuggestionSubjectId(context).map(environment.groupService()::getSubjectGroups).orElse(Set.of());
        environment.groupService().getGroups().stream().sorted(Comparator.naturalOrder()).filter(group -> group.toLowerCase(java.util.Locale.ROOT).startsWith(remaining))
                .filter(group -> !GroupService.DEFAULT_GROUP.equals(group)).filter(group -> !assignedGroups.contains(group)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestRemoveGroups(CommandContext<S> context, SuggestionsBuilder builder) {
        Optional<UUID> subjectId = support.targets().resolveSuggestionSubjectId(context);
        if (subjectId.isEmpty()) {
            return builder.buildFuture();
        }
        String remaining = builder.getRemainingLowerCase();
        environment.groupService().getSubjectGroups(subjectId.get()).stream().sorted(Comparator.naturalOrder())
                .filter(group -> group.toLowerCase(java.util.Locale.ROOT).startsWith(remaining)).filter(group -> !GroupService.DEFAULT_GROUP.equals(group))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestTracks(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        environment.trackService().getTracks().stream().sorted(Comparator.naturalOrder()).filter(track -> track.toLowerCase(java.util.Locale.ROOT).startsWith(remaining))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }
}
