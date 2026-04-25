package me.clutchy.clutchperms.common.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.clutchy.clutchperms.common.command.CommandMessage.Color;
import me.clutchy.clutchperms.common.command.CommandMessage.Segment;
import me.clutchy.clutchperms.common.permission.PermissionValue;

/**
 * Centralizes shared command feedback so every platform adapter reports the same messages.
 */
final class CommandLang {

    static final String STATUS = "ClutchPerms is running with a persisted permission service.";

    static final String ERROR_NO_PERMISSION = "You do not have permission to use ClutchPerms commands.";

    static final String ERROR_OTHER_SOURCE_DENIED = "Only players and console sources can use ClutchPerms commands.";

    private static final String COMMANDS_HEADER = "ClutchPerms commands:";

    private static final String TRY = "Try:";

    private static final String STATUS_PERMISSIONS_FILE = "Permissions file: %s";

    private static final String STATUS_SUBJECTS_FILE = "Subjects file: %s";

    private static final String STATUS_GROUPS_FILE = "Groups file: %s";

    private static final String STATUS_NODES_FILE = "Known nodes file: %s";

    private static final String STATUS_KNOWN_SUBJECTS = "Known subjects: %s";

    private static final String STATUS_KNOWN_GROUPS = "Known groups: %s";

    private static final String STATUS_KNOWN_NODES = "Known permission nodes: %s";

    private static final String STATUS_RESOLVER_CACHE = "Resolver cache: %s subjects, %s node results, %s effective snapshots.";

    private static final String STATUS_RUNTIME_BRIDGE = "Runtime bridge: %s";

    private static final String RELOAD_SUCCESS = "Reloaded permissions, subjects, groups, and known nodes from disk.";

    private static final String VALIDATE_SUCCESS = "Validated permissions, subjects, groups, and known nodes from disk.";

    private static final String ERROR_UNKNOWN_TARGET = "Unknown online player or invalid UUID: %s";

    private static final String ERROR_AMBIGUOUS_KNOWN_USER = "Ambiguous known user %s: %s";

    private static final String ERROR_INVALID_NODE = "Invalid permission node: %s";

    private static final String ERROR_INVALID_VALUE = "Invalid permission value: %s";

    private static final String ERROR_RELOAD_FAILED = "Failed to reload ClutchPerms storage: %s";

    private static final String ERROR_VALIDATE_FAILED = "Failed to validate ClutchPerms storage: %s";

    private static final String ERROR_GROUP_OPERATION_FAILED = "Group operation failed: %s";

    private static final String ERROR_NODE_OPERATION_FAILED = "Known permission node operation failed: %s";

    private static final String ERROR_BACKUP_OPERATION_FAILED = "Backup operation failed: %s";

    private static final String ERROR_UNKNOWN_BACKUP_KIND = "Unknown backup file kind: %s";

    private static final String BACKUPS_EMPTY = "No backups found.";

    private static final String BACKUPS_EMPTY_FOR_KIND = "No backups found for %s.";

    private static final String BACKUPS_LIST = "Backups for %s: %s";

    private static final String BACKUP_RESTORED = "Restored %s from backup %s.";

    private static final String PERMISSIONS_EMPTY = "No permissions set for %s.";

    private static final String PERMISSIONS_LIST = "Permissions for %s: %s";

    private static final String PERMISSION_GET = "%s has %s = %s.";

    private static final String PERMISSION_SET = "Set %s for %s to %s.";

    private static final String PERMISSION_CLEAR = "Cleared %s for %s.";

    private static final String USERS_EMPTY = "No known users.";

    private static final String USERS_LIST = "Known users: %s";

    private static final String USERS_SEARCH_EMPTY = "No users matched %s.";

    private static final String USERS_SEARCH_MATCHES = "Matched users: %s";

    private static final String NODES_EMPTY = "No known permission nodes.";

    private static final String NODES_LIST = "Known permission nodes: %s";

    private static final String NODES_SEARCH_EMPTY = "No known permission nodes matched %s.";

    private static final String NODES_SEARCH_MATCHES = "Matched known permission nodes: %s";

    private static final String NODE_ADDED = "Registered known permission node %s.";

    private static final String NODE_REMOVED = "Removed known permission node %s.";

    private static final String GROUPS_EMPTY = "No groups defined.";

    private static final String GROUPS_LIST = "Groups: %s";

    private static final String GROUP_CREATED = "Created group %s.";

    private static final String GROUP_DELETED = "Deleted group %s.";

    private static final String GROUP_PERMISSIONS_EMPTY = "No permissions set for group %s.";

    private static final String GROUP_PERMISSIONS_LIST = "Permissions for group %s: %s";

    private static final String GROUP_MEMBERS_EMPTY = "Group %s has no explicit members.";

    private static final String GROUP_MEMBERS_LIST = "Members of group %s: %s";

    private static final String GROUP_PARENTS_EMPTY = "Group %s has no parent groups.";

    private static final String GROUP_PARENTS_LIST = "Parents of group %s: %s";

    private static final String GROUP_PARENT_ADDED = "Added parent group %s to group %s.";

    private static final String GROUP_PARENT_REMOVED = "Removed parent group %s from group %s.";

    private static final String GROUP_DEFAULT_IMPLICIT = "Group default applies to every subject implicitly.";

    private static final String GROUP_PERMISSION_GET = "Group %s has %s = %s.";

    private static final String GROUP_PERMISSION_SET = "Set %s for group %s to %s.";

    private static final String GROUP_PERMISSION_CLEAR = "Cleared %s for group %s.";

    private static final String USER_GROUPS_EMPTY = "No groups set for %s.";

    private static final String USER_GROUPS_LIST = "Groups for %s: %s";

    private static final String USER_GROUP_ADDED = "Added %s to group %s.";

    private static final String USER_GROUP_REMOVED = "Removed %s from group %s.";

    private static final String PERMISSION_CHECK = "%s effective %s = %s from %s.";

    private static final String PERMISSION_CHECK_MATCHED = "%s effective %s = %s from %s via %s.";

    private static final String PERMISSION_EXPLAIN_HEADER = "Resolution for %s %s:";

    private static final String PERMISSION_EXPLAIN_RESULT = "Result: %s from %s.";

    private static final String PERMISSION_EXPLAIN_RESULT_MATCHED = "Result: %s from %s via %s.";

    private static final String PERMISSION_EXPLAIN_RESULT_UNSET = "Result: UNSET.";

    private static final String PERMISSION_EXPLAIN_ORDER = "Order: direct > explicit groups by depth > default; exact > closest wildcard > broader wildcard > *; FALSE wins same-rank ties.";

    private static final String PERMISSION_EXPLAIN_MATCH = "Match: %s %s=%s (%s).";

    private static final String PERMISSION_EXPLAIN_NO_MATCHES = "Matches: none.";

    static List<CommandMessage> commandList(String rootLiteral) {
        return List.of(heading(COMMANDS_HEADER), usage(rootLiteral, "status"), usage(rootLiteral, "reload"), usage(rootLiteral, "validate"), usage(rootLiteral, "backup list"),
                usage(rootLiteral, "backup list <permissions|subjects|groups|nodes>"), usage(rootLiteral, "backup restore <permissions|subjects|groups|nodes> <backup-file>"),
                usage(rootLiteral, "user <target> list"), usage(rootLiteral, "user <target> get <node>"), usage(rootLiteral, "user <target> set <node> <true|false>"),
                usage(rootLiteral, "user <target> clear <node>"), usage(rootLiteral, "user <target> groups"), usage(rootLiteral, "user <target> group add <group>"),
                usage(rootLiteral, "user <target> group remove <group>"), usage(rootLiteral, "user <target> check <node>"), usage(rootLiteral, "user <target> explain <node>"),
                usage(rootLiteral, "group list"), usage(rootLiteral, "group <group> create"), usage(rootLiteral, "group <group> delete"), usage(rootLiteral, "group <group> list"),
                usage(rootLiteral, "group <group> get <node>"), usage(rootLiteral, "group <group> set <node> <true|false>"), usage(rootLiteral, "group <group> clear <node>"),
                usage(rootLiteral, "group <group> parents"), usage(rootLiteral, "group <group> parent add <parent>"), usage(rootLiteral, "group <group> parent remove <parent>"),
                usage(rootLiteral, "users list"), usage(rootLiteral, "users search <name>"), usage(rootLiteral, "nodes list"), usage(rootLiteral, "nodes search <query>"),
                usage(rootLiteral, "nodes add <node>"), usage(rootLiteral, "nodes add <node> <description>"), usage(rootLiteral, "nodes remove <node>"));
    }

    static CommandMessage heading(String message) {
        return CommandMessage.of(CommandMessage.bold(message, Color.AQUA));
    }

    static CommandMessage error(String message) {
        return CommandMessage.text(message, Color.RED);
    }

    static CommandMessage detail(String message) {
        return CommandMessage.text(message, Color.GRAY);
    }

    static CommandMessage tryHeader() {
        return CommandMessage.text(TRY, Color.GRAY);
    }

    static CommandMessage usage(String rootLiteral, String command) {
        return CommandMessage.text(command(rootLiteral, command), Color.WHITE);
    }

    static CommandMessage status() {
        return heading(STATUS);
    }

    static CommandMessage statusPermissionsFile(String permissionsFile) {
        return detail(STATUS_PERMISSIONS_FILE, permissionsFile);
    }

    static CommandMessage statusSubjectsFile(String subjectsFile) {
        return detail(STATUS_SUBJECTS_FILE, subjectsFile);
    }

    static CommandMessage statusGroupsFile(String groupsFile) {
        return detail(STATUS_GROUPS_FILE, groupsFile);
    }

    static CommandMessage statusNodesFile(String nodesFile) {
        return detail(STATUS_NODES_FILE, nodesFile);
    }

    static CommandMessage statusKnownSubjects(int knownSubjects) {
        return detail(STATUS_KNOWN_SUBJECTS, knownSubjects);
    }

    static CommandMessage statusKnownGroups(int knownGroups) {
        return detail(STATUS_KNOWN_GROUPS, knownGroups);
    }

    static CommandMessage statusKnownNodes(int knownNodes) {
        return detail(STATUS_KNOWN_NODES, knownNodes);
    }

    static CommandMessage statusResolverCache(int subjects, int nodeResults, int effectiveSnapshots) {
        return detail(STATUS_RESOLVER_CACHE, subjects, nodeResults, effectiveSnapshots);
    }

    static CommandMessage statusRuntimeBridge(String runtimeBridgeStatus) {
        return detail(STATUS_RUNTIME_BRIDGE, runtimeBridgeStatus);
    }

    static CommandMessage reloadSuccess() {
        return success(RELOAD_SUCCESS);
    }

    static CommandMessage validateSuccess() {
        return success(VALIDATE_SUCCESS);
    }

    static CommandMessage unknownTarget(Object target) {
        return error(ERROR_UNKNOWN_TARGET, target);
    }

    static CommandMessage ambiguousKnownUser(String target, String matchedSubjects) {
        return error(ERROR_AMBIGUOUS_KNOWN_USER, target, matchedSubjects);
    }

    static CommandMessage invalidNode(Object node) {
        return error(ERROR_INVALID_NODE, node);
    }

    static CommandMessage invalidValue(Object value) {
        return error(ERROR_INVALID_VALUE, value);
    }

    static CommandMessage reloadFailed(Throwable exception) {
        return error(ERROR_RELOAD_FAILED, exceptionMessage(exception));
    }

    static CommandMessage validateFailed(Throwable exception) {
        return error(ERROR_VALIDATE_FAILED, exceptionMessage(exception));
    }

    static CommandMessage groupOperationFailed(Throwable exception) {
        return error(ERROR_GROUP_OPERATION_FAILED, exceptionMessage(exception));
    }

    static CommandMessage nodeOperationFailed(Throwable exception) {
        return error(ERROR_NODE_OPERATION_FAILED, exceptionMessage(exception));
    }

    static CommandMessage backupOperationFailed(Throwable exception) {
        return error(ERROR_BACKUP_OPERATION_FAILED, exceptionMessage(exception));
    }

    static CommandMessage unknownBackupKind(String token) {
        return error(ERROR_UNKNOWN_BACKUP_KIND, token);
    }

    static CommandMessage backupsEmpty() {
        return detail(BACKUPS_EMPTY);
    }

    static CommandMessage backupsEmpty(String kind) {
        return detail(BACKUPS_EMPTY_FOR_KIND, kind);
    }

    static CommandMessage backupsList(String kind, String backups) {
        return detail(BACKUPS_LIST, kind, backups);
    }

    static CommandMessage backupRestored(String kind, String backupFileName) {
        return success(BACKUP_RESTORED, kind, backupFileName);
    }

    static CommandMessage permissionsEmpty(String subject) {
        return detail(PERMISSIONS_EMPTY, subject);
    }

    static CommandMessage permissionsList(String subject, String assignments) {
        return detail(PERMISSIONS_LIST, subject, assignments);
    }

    static CommandMessage permissionGet(String subject, String node, PermissionValue value) {
        return detail(PERMISSION_GET, subject, node, value.name());
    }

    static CommandMessage permissionSet(String node, String subject, PermissionValue value) {
        return success(PERMISSION_SET, node, subject, value.name());
    }

    static CommandMessage permissionClear(String node, String subject) {
        return success(PERMISSION_CLEAR, node, subject);
    }

    static CommandMessage usersEmpty() {
        return detail(USERS_EMPTY);
    }

    static CommandMessage usersList(String subjects) {
        return detail(USERS_LIST, subjects);
    }

    static CommandMessage usersSearchEmpty(String query) {
        return detail(USERS_SEARCH_EMPTY, query);
    }

    static CommandMessage usersSearchMatches(String subjects) {
        return detail(USERS_SEARCH_MATCHES, subjects);
    }

    static CommandMessage nodesEmpty() {
        return detail(NODES_EMPTY);
    }

    static CommandMessage nodesList(String nodes) {
        return detail(NODES_LIST, nodes);
    }

    static CommandMessage nodesSearchEmpty(String query) {
        return detail(NODES_SEARCH_EMPTY, query);
    }

    static CommandMessage nodesSearchMatches(String nodes) {
        return detail(NODES_SEARCH_MATCHES, nodes);
    }

    static CommandMessage nodeAdded(String node) {
        return success(NODE_ADDED, node);
    }

    static CommandMessage nodeRemoved(String node) {
        return success(NODE_REMOVED, node);
    }

    static CommandMessage groupsEmpty() {
        return detail(GROUPS_EMPTY);
    }

    static CommandMessage groupsList(String groups) {
        return detail(GROUPS_LIST, groups);
    }

    static CommandMessage groupCreated(String group) {
        return success(GROUP_CREATED, group);
    }

    static CommandMessage groupDeleted(String group) {
        return success(GROUP_DELETED, group);
    }

    static CommandMessage groupPermissionsEmpty(String group) {
        return detail(GROUP_PERMISSIONS_EMPTY, group);
    }

    static CommandMessage groupPermissionsList(String group, String assignments) {
        return detail(GROUP_PERMISSIONS_LIST, group, assignments);
    }

    static CommandMessage groupMembersEmpty(String group) {
        return detail(GROUP_MEMBERS_EMPTY, group);
    }

    static CommandMessage groupMembersList(String group, String members) {
        return detail(GROUP_MEMBERS_LIST, group, members);
    }

    static CommandMessage groupParentsEmpty(String group) {
        return detail(GROUP_PARENTS_EMPTY, group);
    }

    static CommandMessage groupParentsList(String group, String parents) {
        return detail(GROUP_PARENTS_LIST, group, parents);
    }

    static CommandMessage groupParentAdded(String group, String parent) {
        return success(GROUP_PARENT_ADDED, parent, group);
    }

    static CommandMessage groupParentRemoved(String group, String parent) {
        return success(GROUP_PARENT_REMOVED, parent, group);
    }

    static CommandMessage groupDefaultImplicit() {
        return detail(GROUP_DEFAULT_IMPLICIT);
    }

    static CommandMessage groupPermissionGet(String group, String node, PermissionValue value) {
        return detail(GROUP_PERMISSION_GET, group, node, value.name());
    }

    static CommandMessage groupPermissionSet(String node, String group, PermissionValue value) {
        return success(GROUP_PERMISSION_SET, node, group, value.name());
    }

    static CommandMessage groupPermissionClear(String node, String group) {
        return success(GROUP_PERMISSION_CLEAR, node, group);
    }

    static CommandMessage userGroupsEmpty(String subject) {
        return detail(USER_GROUPS_EMPTY, subject);
    }

    static CommandMessage userGroupsList(String subject, String groups) {
        return detail(USER_GROUPS_LIST, subject, groups);
    }

    static CommandMessage userGroupAdded(String subject, String group) {
        return success(USER_GROUP_ADDED, subject, group);
    }

    static CommandMessage userGroupRemoved(String subject, String group) {
        return success(USER_GROUP_REMOVED, subject, group);
    }

    static CommandMessage permissionCheck(String subject, String node, PermissionValue value, String source) {
        return detail(PERMISSION_CHECK, subject, node, value.name(), source);
    }

    static CommandMessage permissionCheck(String subject, String node, PermissionValue value, String source, String assignmentNode) {
        return detail(PERMISSION_CHECK_MATCHED, subject, node, value.name(), source, assignmentNode);
    }

    static CommandMessage permissionExplainHeader(String subject, String node) {
        return heading(format(PERMISSION_EXPLAIN_HEADER, subject, node));
    }

    static CommandMessage permissionExplainResult(PermissionValue value, String source) {
        return detail(PERMISSION_EXPLAIN_RESULT, value.name(), source);
    }

    static CommandMessage permissionExplainResult(PermissionValue value, String source, String assignmentNode) {
        return detail(PERMISSION_EXPLAIN_RESULT_MATCHED, value.name(), source, assignmentNode);
    }

    static CommandMessage permissionExplainResultUnset() {
        return detail(PERMISSION_EXPLAIN_RESULT_UNSET);
    }

    static CommandMessage permissionExplainOrder() {
        return detail(PERMISSION_EXPLAIN_ORDER);
    }

    static CommandMessage permissionExplainMatch(String source, String assignmentNode, PermissionValue value, boolean winning) {
        return detail(PERMISSION_EXPLAIN_MATCH, source, assignmentNode, value.name(), winning ? "winner" : "ignored");
    }

    static CommandMessage permissionExplainNoMatches() {
        return detail(PERMISSION_EXPLAIN_NO_MATCHES);
    }

    private static CommandMessage success(String template, Object... arguments) {
        return message(Color.GREEN, template, arguments);
    }

    private static CommandMessage error(String template, Object... arguments) {
        return message(Color.RED, template, arguments);
    }

    private static CommandMessage detail(String template, Object... arguments) {
        return message(Color.GRAY, template, arguments);
    }

    private static CommandMessage message(Color baseColor, String template, Object... arguments) {
        if (arguments.length == 0) {
            return CommandMessage.text(template, baseColor);
        }

        List<Segment> segments = new ArrayList<>();
        int argumentIndex = 0;
        int start = 0;
        while (argumentIndex < arguments.length) {
            int placeholder = template.indexOf("%s", start);
            if (placeholder < 0) {
                break;
            }
            if (placeholder > start) {
                segments.add(CommandMessage.segment(template.substring(start, placeholder), baseColor));
            }
            segments.add(CommandMessage.segment(String.valueOf(arguments[argumentIndex]), Color.YELLOW));
            argumentIndex++;
            start = placeholder + 2;
        }
        if (start < template.length()) {
            segments.add(CommandMessage.segment(template.substring(start), baseColor));
        }
        while (argumentIndex < arguments.length) {
            segments.add(CommandMessage.segment(String.valueOf(arguments[argumentIndex]), Color.YELLOW));
            argumentIndex++;
        }
        return CommandMessage.of(segments.toArray(Segment[]::new));
    }

    private static String command(String rootLiteral, String command) {
        return "/" + rootLiteral + " " + command;
    }

    private static String format(String template, Object... arguments) {
        return String.format(Locale.ROOT, template, arguments);
    }

    private static String exceptionMessage(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message;
    }

    private CommandLang() {
    }
}
