package me.clutchy.clutchperms.common.command;

import java.util.List;
import java.util.Locale;

import me.clutchy.clutchperms.common.permission.PermissionValue;

/**
 * Centralizes shared command feedback text so every platform adapter reports the same messages.
 */
final class CommandLang {

    static final String STATUS = "ClutchPerms is running with a persisted permission service.";

    static final String ERROR_NO_PERMISSION = "You do not have permission to use ClutchPerms commands.";

    static final String ERROR_OTHER_SOURCE_DENIED = "Only players and console sources can use ClutchPerms commands.";

    private static final String COMMANDS_HEADER = "ClutchPerms commands:";

    private static final String STATUS_PERMISSIONS_FILE = "Permissions file: %s";

    private static final String STATUS_SUBJECTS_FILE = "Subjects file: %s";

    private static final String STATUS_GROUPS_FILE = "Groups file: %s";

    private static final String STATUS_KNOWN_SUBJECTS = "Known subjects: %s";

    private static final String STATUS_KNOWN_GROUPS = "Known groups: %s";

    private static final String STATUS_RUNTIME_BRIDGE = "Runtime bridge: %s";

    private static final String RELOAD_SUCCESS = "Reloaded permissions, subjects, and groups from disk.";

    private static final String ERROR_UNKNOWN_TARGET = "Unknown online player or invalid UUID: %s";

    private static final String ERROR_AMBIGUOUS_KNOWN_USER = "Ambiguous known user %s: %s";

    private static final String ERROR_INVALID_NODE = "Invalid permission node: %s";

    private static final String ERROR_RELOAD_FAILED = "Failed to reload ClutchPerms storage: %s";

    private static final String ERROR_GROUP_OPERATION_FAILED = "Group operation failed: %s";

    private static final String PERMISSIONS_EMPTY = "No permissions set for %s.";

    private static final String PERMISSIONS_LIST = "Permissions for %s: %s";

    private static final String PERMISSION_GET = "%s has %s = %s.";

    private static final String PERMISSION_SET = "Set %s for %s to %s.";

    private static final String PERMISSION_CLEAR = "Cleared %s for %s.";

    private static final String USERS_EMPTY = "No known users.";

    private static final String USERS_LIST = "Known users: %s";

    private static final String USERS_SEARCH_EMPTY = "No users matched %s.";

    private static final String USERS_SEARCH_MATCHES = "Matched users: %s";

    private static final String GROUPS_EMPTY = "No groups defined.";

    private static final String GROUPS_LIST = "Groups: %s";

    private static final String GROUP_CREATED = "Created group %s.";

    private static final String GROUP_DELETED = "Deleted group %s.";

    private static final String GROUP_PERMISSIONS_EMPTY = "No permissions set for group %s.";

    private static final String GROUP_PERMISSIONS_LIST = "Permissions for group %s: %s";

    private static final String GROUP_MEMBERS_EMPTY = "Group %s has no explicit members.";

    private static final String GROUP_MEMBERS_LIST = "Members of group %s: %s";

    private static final String GROUP_DEFAULT_IMPLICIT = "Group default applies to every subject implicitly.";

    private static final String GROUP_PERMISSION_GET = "Group %s has %s = %s.";

    private static final String GROUP_PERMISSION_SET = "Set %s for group %s to %s.";

    private static final String GROUP_PERMISSION_CLEAR = "Cleared %s for group %s.";

    private static final String USER_GROUPS_EMPTY = "No groups set for %s.";

    private static final String USER_GROUPS_LIST = "Groups for %s: %s";

    private static final String USER_GROUP_ADDED = "Added %s to group %s.";

    private static final String USER_GROUP_REMOVED = "Removed %s from group %s.";

    private static final String PERMISSION_CHECK = "%s effective %s = %s from %s.";

    static List<String> commandList(String rootLiteral) {
        return List.of(COMMANDS_HEADER, command(rootLiteral, "status"), command(rootLiteral, "reload"), command(rootLiteral, "user <target> list"),
                command(rootLiteral, "user <target> get <node>"), command(rootLiteral, "user <target> set <node> <true|false>"), command(rootLiteral, "user <target> clear <node>"),
                command(rootLiteral, "user <target> groups"), command(rootLiteral, "user <target> group add <group>"), command(rootLiteral, "user <target> group remove <group>"),
                command(rootLiteral, "user <target> check <node>"), command(rootLiteral, "group list"), command(rootLiteral, "group <group> create"),
                command(rootLiteral, "group <group> delete"), command(rootLiteral, "group <group> list"), command(rootLiteral, "group <group> get <node>"),
                command(rootLiteral, "group <group> set <node> <true|false>"), command(rootLiteral, "group <group> clear <node>"), command(rootLiteral, "users list"),
                command(rootLiteral, "users search <name>"));
    }

    static String statusPermissionsFile(String permissionsFile) {
        return format(STATUS_PERMISSIONS_FILE, permissionsFile);
    }

    static String statusSubjectsFile(String subjectsFile) {
        return format(STATUS_SUBJECTS_FILE, subjectsFile);
    }

    static String statusGroupsFile(String groupsFile) {
        return format(STATUS_GROUPS_FILE, groupsFile);
    }

    static String statusKnownSubjects(int knownSubjects) {
        return format(STATUS_KNOWN_SUBJECTS, knownSubjects);
    }

    static String statusKnownGroups(int knownGroups) {
        return format(STATUS_KNOWN_GROUPS, knownGroups);
    }

    static String statusRuntimeBridge(String runtimeBridgeStatus) {
        return format(STATUS_RUNTIME_BRIDGE, runtimeBridgeStatus);
    }

    static String reloadSuccess() {
        return RELOAD_SUCCESS;
    }

    static String unknownTarget(Object target) {
        return format(ERROR_UNKNOWN_TARGET, target);
    }

    static String ambiguousKnownUser(String target, String matchedSubjects) {
        return format(ERROR_AMBIGUOUS_KNOWN_USER, target, matchedSubjects);
    }

    static String invalidNode(Object node) {
        return format(ERROR_INVALID_NODE, node);
    }

    static String reloadFailed(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return format(ERROR_RELOAD_FAILED, message);
    }

    static String groupOperationFailed(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return format(ERROR_GROUP_OPERATION_FAILED, message);
    }

    static String permissionsEmpty(String subject) {
        return format(PERMISSIONS_EMPTY, subject);
    }

    static String permissionsList(String subject, String assignments) {
        return format(PERMISSIONS_LIST, subject, assignments);
    }

    static String permissionGet(String subject, String node, PermissionValue value) {
        return format(PERMISSION_GET, subject, node, value.name());
    }

    static String permissionSet(String node, String subject, PermissionValue value) {
        return format(PERMISSION_SET, node, subject, value.name());
    }

    static String permissionClear(String node, String subject) {
        return format(PERMISSION_CLEAR, node, subject);
    }

    static String usersEmpty() {
        return USERS_EMPTY;
    }

    static String usersList(String subjects) {
        return format(USERS_LIST, subjects);
    }

    static String usersSearchEmpty(String query) {
        return format(USERS_SEARCH_EMPTY, query);
    }

    static String usersSearchMatches(String subjects) {
        return format(USERS_SEARCH_MATCHES, subjects);
    }

    static String groupsEmpty() {
        return GROUPS_EMPTY;
    }

    static String groupsList(String groups) {
        return format(GROUPS_LIST, groups);
    }

    static String groupCreated(String group) {
        return format(GROUP_CREATED, group);
    }

    static String groupDeleted(String group) {
        return format(GROUP_DELETED, group);
    }

    static String groupPermissionsEmpty(String group) {
        return format(GROUP_PERMISSIONS_EMPTY, group);
    }

    static String groupPermissionsList(String group, String assignments) {
        return format(GROUP_PERMISSIONS_LIST, group, assignments);
    }

    static String groupMembersEmpty(String group) {
        return format(GROUP_MEMBERS_EMPTY, group);
    }

    static String groupMembersList(String group, String members) {
        return format(GROUP_MEMBERS_LIST, group, members);
    }

    static String groupDefaultImplicit() {
        return GROUP_DEFAULT_IMPLICIT;
    }

    static String groupPermissionGet(String group, String node, PermissionValue value) {
        return format(GROUP_PERMISSION_GET, group, node, value.name());
    }

    static String groupPermissionSet(String node, String group, PermissionValue value) {
        return format(GROUP_PERMISSION_SET, node, group, value.name());
    }

    static String groupPermissionClear(String node, String group) {
        return format(GROUP_PERMISSION_CLEAR, node, group);
    }

    static String userGroupsEmpty(String subject) {
        return format(USER_GROUPS_EMPTY, subject);
    }

    static String userGroupsList(String subject, String groups) {
        return format(USER_GROUPS_LIST, subject, groups);
    }

    static String userGroupAdded(String subject, String group) {
        return format(USER_GROUP_ADDED, subject, group);
    }

    static String userGroupRemoved(String subject, String group) {
        return format(USER_GROUP_REMOVED, subject, group);
    }

    static String permissionCheck(String subject, String node, PermissionValue value, String source) {
        return format(PERMISSION_CHECK, subject, node, value.name(), source);
    }

    private static String command(String rootLiteral, String command) {
        return "/" + rootLiteral + " " + command;
    }

    private static String format(String template, Object... arguments) {
        return String.format(Locale.ROOT, template, arguments);
    }

    private CommandLang() {
    }
}
