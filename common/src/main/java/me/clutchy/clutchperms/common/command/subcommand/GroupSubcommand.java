package me.clutchy.clutchperms.common.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

import me.clutchy.clutchperms.common.display.DisplaySlot;
import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionValue;

final class GroupSubcommand<S> {

    private final CommandSupport<S> support;

    private final ClutchPermsCommandEnvironment<S> environment;

    GroupSubcommand(CommandSupport<S> support) {
        this.support = support;
        this.environment = support.environment();
    }

    LiteralArgumentBuilder<S> builder() {
        RequiredArgumentBuilder<S, String> group = groupArgument()
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_VIEW, ignored -> targetUsage(context)))
                .requires(source -> support.canUseAny(source, groupPermissions()))
                .then(LiteralArgumentBuilder.<S>literal("create").requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_CREATE))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_CREATE, ignored -> create(context))))
                .then(LiteralArgumentBuilder.<S>literal("delete").requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_DELETE))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_DELETE, ignored -> delete(context))))
                .then(renameCommand())
                .then(LiteralArgumentBuilder.<S>literal("info").requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_INFO))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_INFO, ignored -> info(context))))
                .then(LiteralArgumentBuilder.<S>literal("list").requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_VIEW))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_VIEW, ignored -> show(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.PAGE, StringArgumentType.word())
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_VIEW, ignored -> show(context)))))
                .then(LiteralArgumentBuilder.<S>literal("members").requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_MEMBERS))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_MEMBERS, ignored -> members(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.PAGE, StringArgumentType.word())
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_MEMBERS, ignored -> members(context)))))
                .then(LiteralArgumentBuilder.<S>literal("parents").requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_PARENTS))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_PARENTS, ignored -> parents(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.PAGE, StringArgumentType.word())
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_PARENTS, ignored -> parents(context)))))
                .then(parentCommand()).then(getCommand()).then(setCommand()).then(clearCommand())
                .then(LiteralArgumentBuilder.<S>literal("clear-all").requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_CLEAR_ALL))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_CLEAR_ALL, ignored -> clearAll(context))))
                .then(displayCommand("prefix", DisplaySlot.PREFIX)).then(displayCommand("suffix", DisplaySlot.SUFFIX))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.UNKNOWN, StringArgumentType.word())
                        .requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_VIEW))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_VIEW, ignored -> unknownTargetUsage(context))));

        return LiteralArgumentBuilder.<S>literal("group").requires(source -> support.canUseAny(source, groupPermissions()))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_LIST, ignored -> rootUsage(context)))
                .then(LiteralArgumentBuilder.<S>literal("list").requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_LIST))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_LIST, ignored -> list(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.PAGE, StringArgumentType.word())
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_LIST, ignored -> list(context)))))
                .then(group);
    }

    private int rootUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing group command.", "List groups or choose a group to inspect or mutate.", CommandCatalogs.groupRootUsages());
    }

    private int list(CommandContext<S> context) throws CommandSyntaxException {
        Set<String> groups = environment.groupService().getGroups();
        if (groups.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.groupsEmpty());
            return Command.SINGLE_SUCCESS;
        }

        String rootLiteral = support.currentRootLiteral(context);
        List<CommandPaging.PagedRow> rows = groups.stream().sorted(Comparator.naturalOrder())
                .map(group -> new CommandPaging.PagedRow(group, support.formatting().fullCommand(rootLiteral, "group " + group + " list"))).toList();
        support.paging().sendPagedRows(context, "Groups", rows, "group list");
        return Command.SINGLE_SUCCESS;
    }

    private int targetUsage(CommandContext<S> context) {
        String group = StringArgumentType.getString(context, CommandArguments.GROUP);
        return support.sendUsage(context, "Missing group command.", "Choose what to do with group " + group + ".", CommandCatalogs.groupTargetUsages(group));
    }

    private int unknownTargetUsage(CommandContext<S> context) {
        String group = StringArgumentType.getString(context, CommandArguments.GROUP);
        return support.sendUsage(context, "Unknown group command: " + StringArgumentType.getString(context, CommandArguments.UNKNOWN),
                "Group commands manage definitions, permissions, parents, and members.", CommandCatalogs.groupTargetUsages(group));
    }

    private int create(CommandContext<S> context) throws CommandSyntaxException {
        String groupName = support.getGroupName(context);
        String normalizedGroupName = support.formatting().normalizeGroupName(groupName);
        if (environment.groupService().hasGroup(normalizedGroupName)) {
            environment.sendMessage(context.getSource(), CommandLang.groupAlreadyExists(normalizedGroupName));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().groupSnapshot(normalizedGroupName);
        try {
            environment.groupService().createGroup(groupName);
        } catch (RuntimeException exception) {
            throw support.groupOperationFailed(exception);
        }

        support.audit().recordAudit(context, "group.create", "group", normalizedGroupName, normalizedGroupName, beforeJson, support.audit().groupSnapshot(normalizedGroupName),
                true);
        environment.sendMessage(context.getSource(), CommandLang.groupCreated(normalizedGroupName));
        return Command.SINGLE_SUCCESS;
    }

    private int delete(CommandContext<S> context) throws CommandSyntaxException {
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        if (GroupService.DEFAULT_GROUP.equals(groupName)) {
            throw support.groupOperationFailed(new IllegalArgumentException("default group cannot be deleted"));
        }
        if (GroupService.OP_GROUP.equals(groupName)) {
            throw support.groupOperationFailed(new IllegalArgumentException("op group cannot be deleted"));
        }
        if (!support.audit().confirmDestructiveCommand(context, support.audit().confirmationOperation("group-delete", groupName))) {
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().groupSnapshot(groupName);
        try {
            environment.groupService().deleteGroup(groupName);
        } catch (RuntimeException exception) {
            throw support.groupOperationFailed(exception);
        }

        support.audit().recordAudit(context, "group.delete", "group", groupName, groupName, beforeJson, support.audit().groupSnapshot(groupName), true);
        environment.sendMessage(context.getSource(), CommandLang.groupDeleted(support.formatting().normalizeGroupName(groupName)));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<S> renameCommand() {
        return LiteralArgumentBuilder.<S>literal("rename").requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_RENAME))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_RENAME, ignored -> renameUsage(context)))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.NEW_GROUP, StringArgumentType.word())
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_RENAME, ignored -> rename(context))));
    }

    private int renameUsage(CommandContext<S> context) {
        String group = StringArgumentType.getString(context, CommandArguments.GROUP);
        return support.sendUsage(context, "Missing new group name.", "Choose the new group name.", List.of("group " + group + " rename <new-group>"));
    }

    private int rename(CommandContext<S> context) throws CommandSyntaxException {
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        String newGroupName = support.getNewGroupName(context);
        String normalizedNewGroupName = support.formatting().normalizeGroupName(newGroupName);
        if (support.formatting().normalizeGroupName(groupName).equals(normalizedNewGroupName)) {
            environment.sendMessage(context.getSource(), CommandLang.groupAlreadyNamed(support.formatting().normalizeGroupName(groupName), normalizedNewGroupName));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().renameGroupSnapshot(groupName, normalizedNewGroupName);
        try {
            environment.groupService().renameGroup(groupName, newGroupName);
        } catch (RuntimeException exception) {
            throw support.groupOperationFailed(exception);
        }

        support.audit().recordAudit(context, "group.rename", "group-rename", groupName + "->" + normalizedNewGroupName, groupName + " -> " + normalizedNewGroupName, beforeJson,
                support.audit().renameGroupSnapshot(groupName, normalizedNewGroupName), true);
        environment.sendMessage(context.getSource(),
                CommandLang.groupRenamed(support.formatting().normalizeGroupName(groupName), support.formatting().normalizeGroupName(newGroupName)));
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandContext<S> context) throws CommandSyntaxException {
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        String normalizedGroupName = support.formatting().normalizeGroupName(groupName);
        Map<String, PermissionValue> permissions;
        Set<UUID> members;
        Set<String> parents;
        List<String> childGroups;
        try {
            permissions = environment.groupService().getGroupPermissions(normalizedGroupName);
            members = environment.groupService().getGroupMembers(normalizedGroupName);
            parents = environment.groupService().getGroupParents(normalizedGroupName);
            childGroups = support.formatting().findChildGroups(normalizedGroupName);
        } catch (RuntimeException exception) {
            throw support.groupOperationFailed(exception);
        }

        String rootLiteral = support.currentRootLiteral(context);
        String groupListCommand = support.formatting().fullCommand(rootLiteral, "group " + normalizedGroupName + " list");
        String groupMembersCommand = support.formatting().fullCommand(rootLiteral, "group " + normalizedGroupName + " members");
        String groupParentsCommand = support.formatting().fullCommand(rootLiteral, "group " + normalizedGroupName + " parents");
        String groupPrefixCommand = support.formatting().fullCommand(rootLiteral, "group " + normalizedGroupName + " prefix get");
        String groupSuffixCommand = support.formatting().fullCommand(rootLiteral, "group " + normalizedGroupName + " suffix get");

        List<CommandPaging.PagedRow> rows = new ArrayList<>();
        rows.add(new CommandPaging.PagedRow("name " + normalizedGroupName, groupListCommand));
        if (GroupService.DEFAULT_GROUP.equals(normalizedGroupName)) {
            rows.add(new CommandPaging.PagedRow("default group applies implicitly", groupListCommand));
        } else if (GroupService.OP_GROUP.equals(normalizedGroupName)) {
            rows.add(new CommandPaging.PagedRow("op group grants * to explicit members only", groupMembersCommand));
        }
        rows.add(new CommandPaging.PagedRow("direct permissions " + permissions.size(), groupListCommand));
        rows.add(new CommandPaging.PagedRow("parents " + support.formatting().summarizeValues(parents), groupParentsCommand));
        rows.add(new CommandPaging.PagedRow("child groups " + support.formatting().summarizeValues(childGroups), support.formatting().fullCommand(rootLiteral, "group list")));
        rows.add(new CommandPaging.PagedRow("tracks " + support.formatting().summarizeGroupTracks(normalizedGroupName),
                support.formatting().fullCommand(rootLiteral, "track list")));
        rows.add(new CommandPaging.PagedRow("explicit members " + support.formatting().summarizeGroupMembers(members), groupMembersCommand));
        rows.add(new CommandPaging.PagedRow("prefix " + support.formatting().formatDisplayValue(support.formatting().groupDisplayValue(normalizedGroupName, DisplaySlot.PREFIX)),
                groupPrefixCommand));
        rows.add(new CommandPaging.PagedRow("suffix " + support.formatting().formatDisplayValue(support.formatting().groupDisplayValue(normalizedGroupName, DisplaySlot.SUFFIX)),
                groupSuffixCommand));

        support.paging().sendInfoRows(context, "Group " + normalizedGroupName, rows);
        return Command.SINGLE_SUCCESS;
    }

    private int show(CommandContext<S> context) throws CommandSyntaxException {
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        String normalizedGroupName = support.formatting().normalizeGroupName(groupName);
        Map<String, PermissionValue> permissions;
        Set<UUID> members;
        Set<String> parents;
        try {
            permissions = environment.groupService().getGroupPermissions(groupName);
            members = environment.groupService().getGroupMembers(groupName);
            parents = environment.groupService().getGroupParents(groupName);
        } catch (RuntimeException exception) {
            throw support.groupOperationFailed(exception);
        }

        String rootLiteral = support.currentRootLiteral(context);
        List<CommandPaging.PagedRow> rows = new ArrayList<>();
        support.formatting().addGroupDisplayRows(rows, rootLiteral, normalizedGroupName);
        permissions.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> new CommandPaging.PagedRow("permission " + entry.getKey() + "=" + entry.getValue().name(),
                support.formatting().fullCommand(rootLiteral, "group " + normalizedGroupName + " get " + entry.getKey()))).forEach(rows::add);
        parents.stream().sorted(Comparator.naturalOrder())
                .map(parent -> new CommandPaging.PagedRow("parent " + parent, support.formatting().fullCommand(rootLiteral, "group " + parent + " list"))).forEach(rows::add);
        if (GroupService.DEFAULT_GROUP.equals(normalizedGroupName)) {
            rows.add(new CommandPaging.PagedRow("default group applies implicitly", support.formatting().fullCommand(rootLiteral, "group default list")));
        } else {
            if (GroupService.OP_GROUP.equals(normalizedGroupName)) {
                rows.add(new CommandPaging.PagedRow("op group grants * to explicit members only", support.formatting().fullCommand(rootLiteral, "group op members")));
            }
            members.stream().sorted().map(subjectId -> new CommandPaging.PagedRow("member " + support.formatting().formatSubject(subjectId),
                    support.formatting().fullCommand(rootLiteral, "user " + subjectId + " list"))).forEach(rows::add);
        }

        if (rows.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.groupPermissionsEmpty(normalizedGroupName));
            environment.sendMessage(context.getSource(), CommandLang.groupMembersEmpty(normalizedGroupName));
            return Command.SINGLE_SUCCESS;
        }

        support.paging().sendPagedRows(context, "Group " + normalizedGroupName, rows, "group " + StringArgumentType.getString(context, CommandArguments.GROUP) + " list");
        return Command.SINGLE_SUCCESS;
    }

    private int members(CommandContext<S> context) throws CommandSyntaxException {
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        String normalizedGroupName = support.formatting().normalizeGroupName(groupName);
        Set<UUID> members;
        try {
            members = environment.groupService().getGroupMembers(groupName);
        } catch (RuntimeException exception) {
            throw support.groupOperationFailed(exception);
        }

        if (members.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.groupMembersEmpty(normalizedGroupName));
            return Command.SINGLE_SUCCESS;
        }

        String rootLiteral = support.currentRootLiteral(context);
        List<CommandPaging.PagedRow> rows = members.stream()
                .sorted(Comparator.comparing((UUID subjectId) -> support.formatting().formatSubject(subjectId), String.CASE_INSENSITIVE_ORDER).thenComparing(UUID::toString))
                .map(subjectId -> new CommandPaging.PagedRow(support.formatting().formatSubject(subjectId),
                        support.formatting().fullCommand(rootLiteral, "user " + subjectId + " list")))
                .toList();
        support.paging().sendPagedRows(context, "Members of group " + normalizedGroupName, rows,
                "group " + StringArgumentType.getString(context, CommandArguments.GROUP) + " members");
        return Command.SINGLE_SUCCESS;
    }

    private int parents(CommandContext<S> context) throws CommandSyntaxException {
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        String normalizedGroupName = support.formatting().normalizeGroupName(groupName);
        Set<String> parents;
        try {
            parents = environment.groupService().getGroupParents(groupName);
        } catch (RuntimeException exception) {
            throw support.groupOperationFailed(exception);
        }

        if (parents.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.groupParentsEmpty(normalizedGroupName));
            return Command.SINGLE_SUCCESS;
        }
        String rootLiteral = support.currentRootLiteral(context);
        List<CommandPaging.PagedRow> rows = parents.stream().sorted(Comparator.naturalOrder())
                .map(parent -> new CommandPaging.PagedRow(parent, support.formatting().fullCommand(rootLiteral, "group " + parent + " list"))).toList();
        support.paging().sendPagedRows(context, "Parents of group " + normalizedGroupName, rows,
                "group " + StringArgumentType.getString(context, CommandArguments.GROUP) + " parents");
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<S> parentCommand() {
        return LiteralArgumentBuilder.<S>literal("parent")
                .requires(
                        source -> support.canUseAny(source, PermissionNodes.ADMIN_GROUP_PARENTS, PermissionNodes.ADMIN_GROUP_PARENT_ADD, PermissionNodes.ADMIN_GROUP_PARENT_REMOVE))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_PARENTS, ignored -> parentUsage(context)))
                .then(LiteralArgumentBuilder.<S>literal("add").requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_PARENT_ADD))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_PARENT_ADD, ignored -> parentAddUsage(context)))
                        .then(parentGroupArgument().executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_PARENT_ADD, ignored -> parentAdd(context)))))
                .then(LiteralArgumentBuilder.<S>literal("remove").requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_PARENT_REMOVE))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_PARENT_REMOVE, ignored -> parentRemoveUsage(context)))
                        .then(parentGroupArgument()
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_PARENT_REMOVE, ignored -> parentRemove(context)))))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.UNKNOWN, StringArgumentType.word())
                        .requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_PARENTS))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_PARENTS, ignored -> unknownParentUsage(context))));
    }

    private int parentUsage(CommandContext<S> context) {
        String group = StringArgumentType.getString(context, CommandArguments.GROUP);
        return support.sendUsage(context, "Missing group parent command.", "Add or remove one parent group.",
                List.of("group " + group + " parent add <parent>", "group " + group + " parent remove <parent>"));
    }

    private int parentAddUsage(CommandContext<S> context) {
        String group = StringArgumentType.getString(context, CommandArguments.GROUP);
        return support.sendUsage(context, "Missing parent group.", "Choose the parent group to add.", List.of("group " + group + " parent add <parent>"));
    }

    private int parentAdd(CommandContext<S> context) throws CommandSyntaxException {
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        String parentGroupName = support.requireExistingParentGroup(context, support.getParentGroupName(context));
        if (environment.groupService().getGroupParents(groupName).contains(parentGroupName)) {
            environment.sendMessage(context.getSource(),
                    CommandLang.groupParentAlreadyAdded(support.formatting().normalizeGroupName(groupName), support.formatting().normalizeGroupName(parentGroupName)));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().groupSnapshot(groupName);
        try {
            environment.groupService().addGroupParent(groupName, parentGroupName);
        } catch (RuntimeException exception) {
            throw support.groupOperationFailed(exception);
        }

        support.audit().recordAudit(context, "group.parent.add", "group", groupName, groupName, beforeJson, support.audit().groupSnapshot(groupName), true);
        environment.sendMessage(context.getSource(),
                CommandLang.groupParentAdded(support.formatting().normalizeGroupName(groupName), support.formatting().normalizeGroupName(parentGroupName)));
        return Command.SINGLE_SUCCESS;
    }

    private int parentRemoveUsage(CommandContext<S> context) {
        String group = StringArgumentType.getString(context, CommandArguments.GROUP);
        return support.sendUsage(context, "Missing parent group.", "Choose the parent group to remove.", List.of("group " + group + " parent remove <parent>"));
    }

    private int parentRemove(CommandContext<S> context) throws CommandSyntaxException {
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        String parentGroupName = support.requireExistingParentGroup(context, support.getParentGroupName(context));
        if (!environment.groupService().getGroupParents(groupName).contains(parentGroupName)) {
            environment.sendMessage(context.getSource(),
                    CommandLang.groupParentAlreadyRemoved(support.formatting().normalizeGroupName(groupName), support.formatting().normalizeGroupName(parentGroupName)));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().groupSnapshot(groupName);
        try {
            environment.groupService().removeGroupParent(groupName, parentGroupName);
        } catch (RuntimeException exception) {
            throw support.groupOperationFailed(exception);
        }

        support.audit().recordAudit(context, "group.parent.remove", "group", groupName, groupName, beforeJson, support.audit().groupSnapshot(groupName), true);
        environment.sendMessage(context.getSource(),
                CommandLang.groupParentRemoved(support.formatting().normalizeGroupName(groupName), support.formatting().normalizeGroupName(parentGroupName)));
        return Command.SINGLE_SUCCESS;
    }

    private int unknownParentUsage(CommandContext<S> context) {
        String group = StringArgumentType.getString(context, CommandArguments.GROUP);
        return support.sendUsage(context, "Unknown group parent command: " + StringArgumentType.getString(context, CommandArguments.UNKNOWN),
                "Group parent commands add or remove inheritance.", List.of("group " + group + " parent add <parent>", "group " + group + " parent remove <parent>"));
    }

    private LiteralArgumentBuilder<S> getCommand() {
        return LiteralArgumentBuilder.<S>literal("get").requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_GET))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_GET, ignored -> getUsage(context)))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.NODE, StringArgumentType.greedyString()).suggests(support::suggestPermissionNodes)
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_GET, ignored -> get(context))));
    }

    private int getUsage(CommandContext<S> context) {
        String group = StringArgumentType.getString(context, CommandArguments.GROUP);
        return support.sendUsage(context, "Missing permission node.", "Choose the group permission node to read.", List.of("group " + group + " get <node>"));
    }

    private int get(CommandContext<S> context) throws CommandSyntaxException {
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        String node = support.getNode(context);
        PermissionValue value;
        try {
            value = environment.groupService().getGroupPermission(groupName, node);
        } catch (RuntimeException exception) {
            throw support.groupOperationFailed(exception);
        }
        environment.sendMessage(context.getSource(), CommandLang.groupPermissionGet(support.formatting().normalizeGroupName(groupName), node, value));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<S> setCommand() {
        return LiteralArgumentBuilder.<S>literal("set").requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_SET))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_SET, ignored -> setUsage(context)))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.ASSIGNMENT, StringArgumentType.greedyString()).suggests(support::suggestPermissionAssignment)
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_SET, ignored -> set(context))));
    }

    private int setUsage(CommandContext<S> context) {
        String group = StringArgumentType.getString(context, CommandArguments.GROUP);
        return support.sendUsage(context, "Missing permission assignment.", "Set a group permission node to true or false.",
                List.of("group " + group + " set <node> <true|false>"));
    }

    private int set(CommandContext<S> context) throws CommandSyntaxException {
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        CommandSupport.PermissionAssignment assignment = support.getPermissionAssignment(context);
        if (environment.groupService().getGroupPermission(groupName, assignment.node()) == assignment.value()) {
            environment.sendMessage(context.getSource(),
                    CommandLang.groupPermissionAlreadySet(assignment.node(), support.formatting().normalizeGroupName(groupName), assignment.value()));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().groupPermissionsSnapshot(groupName);
        try {
            environment.groupService().setGroupPermission(groupName, assignment.node(), assignment.value());
        } catch (RuntimeException exception) {
            throw support.groupOperationFailed(exception);
        }

        support.audit().recordAudit(context, "group.permission.set", "group-permissions", groupName, groupName, beforeJson, support.audit().groupPermissionsSnapshot(groupName),
                true);
        environment.sendMessage(context.getSource(), CommandLang.groupPermissionSet(assignment.node(), support.formatting().normalizeGroupName(groupName), assignment.value()));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<S> clearCommand() {
        return LiteralArgumentBuilder.<S>literal("clear").requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_CLEAR))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_CLEAR, ignored -> clearUsage(context)))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.NODE, StringArgumentType.greedyString()).suggests(support::suggestPermissionNodes)
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_CLEAR, ignored -> clear(context))));
    }

    private int clearUsage(CommandContext<S> context) {
        String group = StringArgumentType.getString(context, CommandArguments.GROUP);
        return support.sendUsage(context, "Missing permission node.", "Choose the group permission node to clear.", List.of("group " + group + " clear <node>"));
    }

    private int clear(CommandContext<S> context) throws CommandSyntaxException {
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        String node = support.getNode(context);
        if (environment.groupService().getGroupPermission(groupName, node) == PermissionValue.UNSET) {
            environment.sendMessage(context.getSource(), CommandLang.groupPermissionAlreadyClear(node, support.formatting().normalizeGroupName(groupName)));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().groupPermissionsSnapshot(groupName);
        try {
            environment.groupService().clearGroupPermission(groupName, node);
        } catch (RuntimeException exception) {
            throw support.groupOperationFailed(exception);
        }
        support.audit().recordAudit(context, "group.permission.clear", "group-permissions", groupName, groupName, beforeJson, support.audit().groupPermissionsSnapshot(groupName),
                true);
        environment.sendMessage(context.getSource(), CommandLang.groupPermissionClear(node, support.formatting().normalizeGroupName(groupName)));
        return Command.SINGLE_SUCCESS;
    }

    private int clearAll(CommandContext<S> context) throws CommandSyntaxException {
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        if (GroupService.OP_GROUP.equals(groupName)) {
            throw support.groupOperationFailed(new IllegalArgumentException("op group permissions are protected"));
        }
        if (environment.groupService().getGroupPermissions(groupName).isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.groupPermissionsEmpty(groupName));
            return Command.SINGLE_SUCCESS;
        }
        if (!support.audit().confirmDestructiveCommand(context, support.audit().confirmationOperation("group-clear-all", groupName))) {
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().groupPermissionsSnapshot(groupName);
        int removedPermissions;
        try {
            removedPermissions = environment.groupService().clearGroupPermissions(groupName);
        } catch (RuntimeException exception) {
            throw support.groupOperationFailed(exception);
        }

        String normalizedGroupName = support.formatting().normalizeGroupName(groupName);
        if (removedPermissions == 0) {
            environment.sendMessage(context.getSource(), CommandLang.groupPermissionsEmpty(normalizedGroupName));
        } else {
            support.audit().recordAudit(context, "group.permission.clear-all", "group-permissions", normalizedGroupName, normalizedGroupName, beforeJson,
                    support.audit().groupPermissionsSnapshot(normalizedGroupName), true);
            environment.sendMessage(context.getSource(), CommandLang.groupPermissionsClearAll(normalizedGroupName, removedPermissions));
        }
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<S> displayCommand(String literal, DisplaySlot slot) {
        return LiteralArgumentBuilder.<S>literal(literal)
                .requires(source -> support.canUseAny(source, PermissionNodes.ADMIN_GROUP_DISPLAY_VIEW, PermissionNodes.ADMIN_GROUP_DISPLAY_SET,
                        PermissionNodes.ADMIN_GROUP_DISPLAY_CLEAR))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_DISPLAY_VIEW, ignored -> displayUsage(context, slot)))
                .then(LiteralArgumentBuilder.<S>literal("get").requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_DISPLAY_VIEW))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_DISPLAY_VIEW, ignored -> displayGet(context, slot))))
                .then(LiteralArgumentBuilder.<S>literal("set").requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_DISPLAY_SET))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_DISPLAY_SET, ignored -> displaySetUsage(context, slot)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.DISPLAY_VALUE, StringArgumentType.greedyString())
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_DISPLAY_SET, ignored -> displaySet(context, slot)))))
                .then(LiteralArgumentBuilder.<S>literal("clear").requires(source -> support.canUse(source, PermissionNodes.ADMIN_GROUP_DISPLAY_CLEAR))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_GROUP_DISPLAY_CLEAR, ignored -> displayClear(context, slot))));
    }

    private int displayUsage(CommandContext<S> context, DisplaySlot slot) {
        String group = StringArgumentType.getString(context, CommandArguments.GROUP);
        return support.sendUsage(context, "Missing group " + slot.label() + " command.", "Get, set, or clear this group's " + slot.label() + ".",
                List.of("group " + group + " " + slot.label() + " get", "group " + group + " " + slot.label() + " set <text>", "group " + group + " " + slot.label() + " clear"));
    }

    private int displaySetUsage(CommandContext<S> context, DisplaySlot slot) {
        String group = StringArgumentType.getString(context, CommandArguments.GROUP);
        return support.sendUsage(context, "Missing display text.", "Use ampersand formatting codes like &7, &a, &l, &o, &r, and && for a literal ampersand.",
                List.of("group " + group + " " + slot.label() + " set <text>"));
    }

    private int displayGet(CommandContext<S> context, DisplaySlot slot) throws CommandSyntaxException {
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        Optional<DisplayText> value = support.formatting().groupDisplayValue(groupName, slot);
        if (value.isPresent()) {
            environment.sendMessage(context.getSource(), CommandLang.groupDisplayGet(groupName, slot.label(), value.get().rawText()));
        } else {
            environment.sendMessage(context.getSource(), CommandLang.groupDisplayUnset(groupName, slot.label()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int displaySet(CommandContext<S> context, DisplaySlot slot) throws CommandSyntaxException {
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        DisplayText displayText = support.getDisplayText(context);
        if (Optional.of(displayText).equals(support.formatting().groupDisplayValue(groupName, slot))) {
            environment.sendMessage(context.getSource(), CommandLang.groupDisplayAlreadySet(slot.label(), groupName, displayText.rawText()));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().groupDisplaySnapshot(groupName);
        try {
            if (slot == DisplaySlot.PREFIX) {
                environment.groupService().setGroupPrefix(groupName, displayText);
            } else {
                environment.groupService().setGroupSuffix(groupName, displayText);
            }
        } catch (RuntimeException exception) {
            throw support.displayOperationFailed(exception);
        }
        support.audit().recordAudit(context, "group.display." + slot.label() + ".set", "group-display", groupName, groupName, beforeJson,
                support.audit().groupDisplaySnapshot(groupName), true);
        environment.sendMessage(context.getSource(), CommandLang.groupDisplaySet(slot.label(), groupName, displayText.rawText()));
        return Command.SINGLE_SUCCESS;
    }

    private int displayClear(CommandContext<S> context, DisplaySlot slot) throws CommandSyntaxException {
        String groupName = support.requireExistingGroup(context, support.getGroupName(context));
        if (support.formatting().groupDisplayValue(groupName, slot).isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.groupDisplayAlreadyClear(slot.label(), groupName));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().groupDisplaySnapshot(groupName);
        try {
            if (slot == DisplaySlot.PREFIX) {
                environment.groupService().clearGroupPrefix(groupName);
            } else {
                environment.groupService().clearGroupSuffix(groupName);
            }
        } catch (RuntimeException exception) {
            throw support.displayOperationFailed(exception);
        }
        support.audit().recordAudit(context, "group.display." + slot.label() + ".clear", "group-display", groupName, groupName, beforeJson,
                support.audit().groupDisplaySnapshot(groupName), true);
        environment.sendMessage(context.getSource(), CommandLang.groupDisplayClear(slot.label(), groupName));
        return Command.SINGLE_SUCCESS;
    }

    private String[] groupPermissions() {
        return new String[]{PermissionNodes.ADMIN_GROUP_LIST, PermissionNodes.ADMIN_GROUP_INFO, PermissionNodes.ADMIN_GROUP_CREATE, PermissionNodes.ADMIN_GROUP_DELETE,
                PermissionNodes.ADMIN_GROUP_RENAME, PermissionNodes.ADMIN_GROUP_VIEW, PermissionNodes.ADMIN_GROUP_GET, PermissionNodes.ADMIN_GROUP_SET,
                PermissionNodes.ADMIN_GROUP_CLEAR, PermissionNodes.ADMIN_GROUP_CLEAR_ALL, PermissionNodes.ADMIN_GROUP_MEMBERS, PermissionNodes.ADMIN_GROUP_PARENTS,
                PermissionNodes.ADMIN_GROUP_PARENT_ADD, PermissionNodes.ADMIN_GROUP_PARENT_REMOVE, PermissionNodes.ADMIN_GROUP_DISPLAY_VIEW,
                PermissionNodes.ADMIN_GROUP_DISPLAY_SET, PermissionNodes.ADMIN_GROUP_DISPLAY_CLEAR};
    }

    private RequiredArgumentBuilder<S, String> groupArgument() {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.GROUP, StringArgumentType.word()).suggests(this::suggestGroups);
    }

    private RequiredArgumentBuilder<S, String> parentGroupArgument() {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.PARENT, StringArgumentType.word()).suggests(this::suggestParentGroups);
    }

    private CompletableFuture<Suggestions> suggestGroups(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        environment.groupService().getGroups().stream().sorted(Comparator.naturalOrder()).filter(group -> group.toLowerCase(java.util.Locale.ROOT).startsWith(remaining))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestParentGroups(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        String normalizedGroupName;
        Set<String> existingParents;
        try {
            normalizedGroupName = StringArgumentType.getString(context, CommandArguments.GROUP).trim().toLowerCase(java.util.Locale.ROOT);
            existingParents = environment.groupService().getGroupParents(normalizedGroupName);
        } catch (IllegalArgumentException exception) {
            normalizedGroupName = "";
            existingParents = Set.of();
        }

        String currentGroupName = normalizedGroupName;
        Set<String> currentParents = existingParents;
        if (GroupService.OP_GROUP.equals(currentGroupName)) {
            return builder.buildFuture();
        }
        environment.groupService().getGroups().stream().sorted(Comparator.naturalOrder()).filter(group -> !group.equals(currentGroupName))
                .filter(group -> !GroupService.OP_GROUP.equals(group)).filter(group -> !currentParents.contains(group))
                .filter(group -> group.toLowerCase(java.util.Locale.ROOT).startsWith(remaining)).forEach(builder::suggest);
        return builder.buildFuture();
    }
}
