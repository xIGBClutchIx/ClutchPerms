package me.clutchy.clutchperms.common.command;

import java.util.List;
import java.util.Locale;

import me.clutchy.clutchperms.common.PermissionValue;

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

    private static final String STATUS_KNOWN_SUBJECTS = "Known subjects: %s";

    private static final String STATUS_RUNTIME_BRIDGE = "Runtime bridge: %s";

    private static final String ERROR_UNKNOWN_TARGET = "Unknown online player or invalid UUID: %s";

    private static final String ERROR_AMBIGUOUS_KNOWN_USER = "Ambiguous known user %s: %s";

    private static final String ERROR_INVALID_NODE = "Invalid permission node: %s";

    private static final String PERMISSIONS_EMPTY = "No permissions set for %s.";

    private static final String PERMISSIONS_LIST = "Permissions for %s: %s";

    private static final String PERMISSION_GET = "%s has %s = %s.";

    private static final String PERMISSION_SET = "Set %s for %s to %s.";

    private static final String PERMISSION_CLEAR = "Cleared %s for %s.";

    private static final String USERS_EMPTY = "No known users.";

    private static final String USERS_LIST = "Known users: %s";

    private static final String USERS_SEARCH_EMPTY = "No users matched %s.";

    private static final String USERS_SEARCH_MATCHES = "Matched users: %s";

    static List<String> commandList(String rootLiteral) {
        return List.of(COMMANDS_HEADER, command(rootLiteral, "status"), command(rootLiteral, "user <target> list"), command(rootLiteral, "user <target> get <node>"),
                command(rootLiteral, "user <target> set <node> <true|false>"), command(rootLiteral, "user <target> clear <node>"), command(rootLiteral, "users list"),
                command(rootLiteral, "users search <name>"));
    }

    static String statusPermissionsFile(String permissionsFile) {
        return format(STATUS_PERMISSIONS_FILE, permissionsFile);
    }

    static String statusSubjectsFile(String subjectsFile) {
        return format(STATUS_SUBJECTS_FILE, subjectsFile);
    }

    static String statusKnownSubjects(int knownSubjects) {
        return format(STATUS_KNOWN_SUBJECTS, knownSubjects);
    }

    static String statusRuntimeBridge(String runtimeBridgeStatus) {
        return format(STATUS_RUNTIME_BRIDGE, runtimeBridgeStatus);
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

    private static String command(String rootLiteral, String command) {
        return "/" + rootLiteral + " " + command;
    }

    private static String format(String template, Object... arguments) {
        return String.format(Locale.ROOT, template, arguments);
    }

    private CommandLang() {
    }
}
