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

    private static final String COMMANDS_HEADER = "ClutchPerms commands (page %s/%s):";

    private static final String TRY = "Try one:";

    private static final String CLICK_TO_PASTE = "Click to paste: %s";

    private static final String CLICK_TO_OPEN = "Click to open page %s.";

    private static final String PERMISSION = "Permission: %s";

    private static final String PAGE_STATUS = "Page %s/%s";

    private static final String PREVIOUS_PAGE = "< Prev";

    private static final String NEXT_PAGE = "Next >";

    private static final String INVALID_PAGE = "Invalid page: %s";

    private static final String PAGE_STARTS_AT_ONE = "Pages start at 1.";

    private static final String PAGE_OUT_OF_RANGE = "Page %s is out of range.";

    private static final String AVAILABLE_PAGES = "Available pages: 1-%s.";

    private static final String STATUS_PERMISSIONS_FILE = "Permissions file: %s";

    private static final String STATUS_SUBJECTS_FILE = "Subjects file: %s";

    private static final String STATUS_GROUPS_FILE = "Groups file: %s";

    private static final String STATUS_NODES_FILE = "Known nodes file: %s";

    private static final String STATUS_CONFIG_FILE = "Config file: %s";

    private static final String STATUS_BACKUP_RETENTION = "Backup retention: newest %s per storage kind.";

    private static final String STATUS_COMMAND_PAGE_SIZES = "Command page sizes: help %s, lists %s.";

    private static final String STATUS_KNOWN_SUBJECTS = "Known subjects: %s";

    private static final String STATUS_KNOWN_GROUPS = "Known groups: %s";

    private static final String STATUS_KNOWN_NODES = "Known permission nodes: %s";

    private static final String STATUS_RESOLVER_CACHE = "Resolver cache: %s subjects, %s node results, %s effective snapshots.";

    private static final String STATUS_RUNTIME_BRIDGE = "Runtime bridge: %s";

    private static final String RELOAD_SUCCESS = "Reloaded config, permissions, subjects, groups, and known nodes from disk.";

    private static final String VALIDATE_SUCCESS = "Validated config, permissions, subjects, groups, and known nodes from disk.";

    private static final String ERROR_UNKNOWN_USER_TARGET = "Unknown user target: %s";

    private static final String ERROR_AMBIGUOUS_KNOWN_USER_TARGET = "Ambiguous known user: %s";

    private static final String USER_TARGET_FORMS = "Use an exact online name, stored last-known name, or UUID.";

    private static final String AMBIGUOUS_KNOWN_USER_DETAIL = "More than one stored subject matches %s.";

    private static final String CLOSEST_ONLINE_USERS = "Closest online players: %s";

    private static final String CLOSEST_KNOWN_USERS = "Closest known users: %s";

    private static final String NO_USER_TARGET_MATCHES = "No close user matches.";

    private static final String ERROR_UNKNOWN_GROUP_TARGET = "Unknown group: %s";

    private static final String ERROR_UNKNOWN_PARENT_GROUP_TARGET = "Unknown parent group: %s";

    private static final String CLOSEST_GROUPS = "Closest groups: %s";

    private static final String NO_GROUPS_DEFINED = "No groups are defined.";

    private static final String NO_GROUP_TARGET_MATCHES = "No close group matches.";

    private static final String ERROR_INVALID_NODE = "Invalid permission node: %s";

    private static final String ERROR_INVALID_VALUE = "Invalid permission value: %s";

    private static final String ERROR_RELOAD_FAILED = "Failed to reload ClutchPerms storage: %s";

    private static final String ERROR_VALIDATE_FAILED = "Failed to validate ClutchPerms storage: %s";

    private static final String ERROR_PERMISSION_OPERATION_FAILED = "Permission operation failed: %s";

    private static final String ERROR_GROUP_OPERATION_FAILED = "Group operation failed: %s";

    private static final String ERROR_NODE_OPERATION_FAILED = "Known permission node operation failed: %s";

    private static final String ERROR_BACKUP_OPERATION_FAILED = "Backup operation failed: %s";

    private static final String ERROR_CONFIG_OPERATION_FAILED = "Config operation failed: %s";

    private static final String CONFIG_HEADER = "ClutchPerms config:";

    private static final String CONFIG_ROW = "%s = %s (%s; range %s-%s)";

    private static final String CONFIG_GET = "%s = %s (%s; range %s-%s)";

    private static final String CONFIG_UPDATED = "Updated config %s: %s -> %s. Runtime reloaded.";

    private static final String CONFIG_RESET = "Reset config %s: %s -> %s. Runtime reloaded.";

    private static final String CONFIG_RESET_ALL = "Reset all config values to defaults. Runtime reloaded.";

    private static final String CONFIG_ALREADY_SET = "Config %s is already %s.";

    private static final String CONFIG_ALREADY_DEFAULTS = "Config already matches defaults.";

    private static final String ERROR_UNKNOWN_CONFIG_KEY = "Unknown config key: %s";

    private static final String VALID_CONFIG_KEYS = "Valid config keys: %s";

    private static final String CLOSEST_CONFIG_KEYS = "Closest config keys: %s";

    private static final String ERROR_INVALID_CONFIG_VALUE = "Invalid config value for %s: %s";

    private static final String CONFIG_VALUE_RANGE = "%s must be an integer between %s and %s.";

    private static final String ERROR_UNKNOWN_BACKUP_KIND = "Unknown backup file kind: %s";

    private static final String VALID_BACKUP_KINDS = "Valid backup kinds: %s";

    private static final String CLOSEST_BACKUP_KINDS = "Closest backup kinds: %s";

    private static final String ERROR_UNKNOWN_BACKUP_FILE = "Unknown %s backup file: %s";

    private static final String NO_BACKUPS_FOR_KIND = "No backups exist for %s.";

    private static final String CLOSEST_BACKUP_FILES = "Closest backup files: %s";

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

    private static final String ERROR_UNKNOWN_MANUAL_NODE = "Known permission node is not manually registered: %s";

    private static final String ERROR_NON_MANUAL_NODE = "Known permission node is supplied by %s and cannot be removed: %s";

    private static final String ONLY_MANUAL_NODES_REMOVABLE = "Only manually registered known permission nodes can be removed.";

    private static final String CLOSEST_MANUAL_NODES = "Closest manual known permission nodes: %s";

    private static final String NO_MANUAL_NODES = "No manual known permission nodes are registered.";

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

    private static final String TARGET_MATCH = "  %s";

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
        return interactiveCommandSyntax("", rootLiteral, command, CommandMessage.clickSuggest(fullCommand(rootLiteral, command)), pasteHover(fullCommand(rootLiteral, command)));
    }

    static CommandMessage suggestion(String rootLiteral, String command) {
        return interactiveCommandSyntax("  ", rootLiteral, command, CommandMessage.clickSuggest(fullCommand(rootLiteral, command)), pasteHover(fullCommand(rootLiteral, command)));
    }

    static CommandMessage commandListHeader(int page, int totalPages) {
        return heading(format(COMMANDS_HEADER, page, totalPages));
    }

    static CommandMessage commandHelpEntry(String rootLiteral, String command, String permission, String description) {
        String fullCommand = fullCommand(rootLiteral, command);
        return interactiveCommandSyntax("", rootLiteral, command, CommandMessage.clickSuggest(fullCommand),
                CommandMessage.of(CommandMessage.segment(description + "\n", Color.GRAY), CommandMessage.segment(format(PERMISSION, permission) + "\n", Color.GRAY),
                        CommandMessage.segment(format(CLICK_TO_PASTE, fullCommand), Color.WHITE)));
    }

    static CommandMessage listHeader(String title, int page, int totalPages) {
        return heading(title + " (page " + page + "/" + totalPages + "):");
    }

    static CommandMessage listRow(String text, String command) {
        return CommandMessage.of(CommandMessage.segment("  ", Color.GRAY), CommandMessage.segment(text, Color.WHITE)).withInteraction(CommandMessage.clickSuggest(command),
                pasteHover(command));
    }

    static CommandMessage pageNavigation(String previousCommand, int previousPage, int page, int totalPages, String nextCommand, int nextPage) {
        CommandMessage message = null;
        if (previousCommand != null) {
            message = append(message, navigationAction(PREVIOUS_PAGE, previousCommand, previousPage));
            message = append(message, CommandMessage.text(" | ", Color.GRAY));
        }
        message = append(message, CommandMessage.text(format(PAGE_STATUS, page, totalPages), Color.GRAY));
        if (nextCommand != null) {
            message = append(message, CommandMessage.text(" | ", Color.GRAY));
            message = append(message, navigationAction(NEXT_PAGE, nextCommand, nextPage));
        }
        return message;
    }

    static CommandMessage invalidPage(String page) {
        return error(INVALID_PAGE, page);
    }

    static CommandMessage pageStartsAtOne() {
        return detail(PAGE_STARTS_AT_ONE);
    }

    static CommandMessage pageOutOfRange(int page) {
        return error(PAGE_OUT_OF_RANGE, page);
    }

    static CommandMessage availablePages(int totalPages) {
        return detail(AVAILABLE_PAGES, totalPages);
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

    static CommandMessage statusConfigFile(String configFile) {
        return detail(STATUS_CONFIG_FILE, configFile);
    }

    static CommandMessage statusBackupRetention(int retentionLimit) {
        return detail(STATUS_BACKUP_RETENTION, retentionLimit);
    }

    static CommandMessage statusCommandPageSizes(int helpPageSize, int resultPageSize) {
        return detail(STATUS_COMMAND_PAGE_SIZES, helpPageSize, resultPageSize);
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

    static CommandMessage unknownUserTarget(String target) {
        return error(ERROR_UNKNOWN_USER_TARGET, target);
    }

    static CommandMessage ambiguousKnownUser(String target) {
        return error(ERROR_AMBIGUOUS_KNOWN_USER_TARGET, target);
    }

    static CommandMessage userTargetForms() {
        return detail(USER_TARGET_FORMS);
    }

    static CommandMessage ambiguousKnownUserDetail(String target) {
        return detail(AMBIGUOUS_KNOWN_USER_DETAIL, target);
    }

    static CommandMessage closestOnlineUsers(String matches) {
        return detail(CLOSEST_ONLINE_USERS, matches);
    }

    static CommandMessage closestKnownUsers(String matches) {
        return detail(CLOSEST_KNOWN_USERS, matches);
    }

    static CommandMessage noUserTargetMatches() {
        return detail(NO_USER_TARGET_MATCHES);
    }

    static CommandMessage unknownGroupTarget(String group) {
        return error(ERROR_UNKNOWN_GROUP_TARGET, group);
    }

    static CommandMessage unknownParentGroupTarget(String group) {
        return error(ERROR_UNKNOWN_PARENT_GROUP_TARGET, group);
    }

    static CommandMessage closestGroups(String matches) {
        return detail(CLOSEST_GROUPS, matches);
    }

    static CommandMessage noGroupsDefined() {
        return detail(NO_GROUPS_DEFINED);
    }

    static CommandMessage noGroupTargetMatches() {
        return detail(NO_GROUP_TARGET_MATCHES);
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

    static CommandMessage permissionOperationFailed(Throwable exception) {
        return error(ERROR_PERMISSION_OPERATION_FAILED, exceptionMessage(exception));
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

    static CommandMessage configOperationFailed(Throwable exception) {
        return error(ERROR_CONFIG_OPERATION_FAILED, exceptionMessage(exception));
    }

    static CommandMessage configHeader() {
        return heading(CONFIG_HEADER);
    }

    static CommandMessage configRow(String key, int value, String description, int minimum, int maximum) {
        return detail(CONFIG_ROW, key, value, description, minimum, maximum);
    }

    static CommandMessage configGet(String key, int value, String description, int minimum, int maximum) {
        return detail(CONFIG_GET, key, value, description, minimum, maximum);
    }

    static CommandMessage configUpdated(String key, int oldValue, int newValue) {
        return success(CONFIG_UPDATED, key, oldValue, newValue);
    }

    static CommandMessage configReset(String key, int oldValue, int newValue) {
        return success(CONFIG_RESET, key, oldValue, newValue);
    }

    static CommandMessage configResetAll() {
        return success(CONFIG_RESET_ALL);
    }

    static CommandMessage configAlreadySet(String key, int value) {
        return success(CONFIG_ALREADY_SET, key, value);
    }

    static CommandMessage configAlreadyDefaults() {
        return success(CONFIG_ALREADY_DEFAULTS);
    }

    static CommandMessage unknownConfigKey(String key) {
        return error(ERROR_UNKNOWN_CONFIG_KEY, key);
    }

    static CommandMessage validConfigKeys(String keys) {
        return detail(VALID_CONFIG_KEYS, keys);
    }

    static CommandMessage closestConfigKeys(String keys) {
        return detail(CLOSEST_CONFIG_KEYS, keys);
    }

    static CommandMessage invalidConfigValue(String key, String value) {
        return error(ERROR_INVALID_CONFIG_VALUE, key, value);
    }

    static CommandMessage configValueRange(String key, int minimum, int maximum) {
        return detail(CONFIG_VALUE_RANGE, key, minimum, maximum);
    }

    static CommandMessage unknownBackupKind(String token) {
        return error(ERROR_UNKNOWN_BACKUP_KIND, token);
    }

    static CommandMessage validBackupKinds(String kinds) {
        return detail(VALID_BACKUP_KINDS, kinds);
    }

    static CommandMessage closestBackupKinds(String kinds) {
        return detail(CLOSEST_BACKUP_KINDS, kinds);
    }

    static CommandMessage unknownBackupFile(String kind, String backupFileName) {
        return error(ERROR_UNKNOWN_BACKUP_FILE, kind, backupFileName);
    }

    static CommandMessage noBackupsForKind(String kind) {
        return detail(NO_BACKUPS_FOR_KIND, kind);
    }

    static CommandMessage closestBackupFiles(String backupFileNames) {
        return detail(CLOSEST_BACKUP_FILES, backupFileNames);
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

    static CommandMessage unknownManualNode(String node) {
        return error(ERROR_UNKNOWN_MANUAL_NODE, node);
    }

    static CommandMessage nonManualNode(String source, String node) {
        return error(ERROR_NON_MANUAL_NODE, source, node);
    }

    static CommandMessage onlyManualNodesRemovable() {
        return detail(ONLY_MANUAL_NODES_REMOVABLE);
    }

    static CommandMessage closestManualNodes(String nodes) {
        return detail(CLOSEST_MANUAL_NODES, nodes);
    }

    static CommandMessage noManualNodes() {
        return detail(NO_MANUAL_NODES);
    }

    static CommandMessage targetMatch(String match) {
        return detail(TARGET_MATCH, match);
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

    private static CommandMessage commandSyntax(String prefix, String rootLiteral, String command) {
        List<Segment> segments = new ArrayList<>();
        if (!prefix.isEmpty()) {
            segments.add(CommandMessage.segment(prefix, Color.GRAY));
        }

        String fullCommand = "/" + rootLiteral + " " + command;
        int start = 0;
        for (int index = 0; index < fullCommand.length(); index++) {
            char current = fullCommand.charAt(index);
            char close = current == '<' ? '>' : current == '[' ? ']' : 0;
            if (close == 0) {
                continue;
            }

            if (index > start) {
                segments.add(CommandMessage.segment(fullCommand.substring(start, index), Color.WHITE));
            }
            int end = fullCommand.indexOf(close, index + 1);
            if (end < 0) {
                segments.add(CommandMessage.segment(fullCommand.substring(index), Color.YELLOW));
                return CommandMessage.of(segments.toArray(Segment[]::new));
            }
            appendSyntaxArgument(segments, fullCommand.substring(index, end + 1));
            index = end;
            start = end + 1;
        }
        if (start < fullCommand.length()) {
            segments.add(CommandMessage.segment(fullCommand.substring(start), Color.WHITE));
        }
        return CommandMessage.of(segments.toArray(Segment[]::new));
    }

    private static CommandMessage interactiveCommandSyntax(String prefix, String rootLiteral, String command, CommandMessage.Click click, CommandMessage hover) {
        return commandSyntax(prefix, rootLiteral, command).withInteraction(click, hover);
    }

    private static void appendSyntaxArgument(List<Segment> segments, String argument) {
        if (argument.length() < 2) {
            segments.add(CommandMessage.segment(argument, Color.YELLOW));
            return;
        }

        String content = argument.substring(1, argument.length() - 1);
        segments.add(CommandMessage.segment(argument.substring(0, 1), Color.GRAY));
        if (content.contains("|")) {
            appendChoiceSegments(segments, content);
        } else {
            segments.add(CommandMessage.segment(content, Color.YELLOW));
        }
        segments.add(CommandMessage.segment(argument.substring(argument.length() - 1), Color.GRAY));
    }

    private static void appendChoiceSegments(List<Segment> segments, String choices) {
        int start = 0;
        for (int index = 0; index < choices.length(); index++) {
            if (choices.charAt(index) != '|') {
                continue;
            }
            if (index > start) {
                segments.add(CommandMessage.segment(choices.substring(start, index), Color.GREEN));
            }
            segments.add(CommandMessage.segment("|", Color.GRAY));
            start = index + 1;
        }
        if (start < choices.length()) {
            segments.add(CommandMessage.segment(choices.substring(start), Color.GREEN));
        }
    }

    private static String format(String template, Object... arguments) {
        return String.format(Locale.ROOT, template, arguments);
    }

    private static String fullCommand(String rootLiteral, String command) {
        return "/" + rootLiteral + (command.isBlank() ? "" : " " + command);
    }

    private static CommandMessage pasteHover(String command) {
        return CommandMessage.text(format(CLICK_TO_PASTE, command), Color.GRAY);
    }

    private static CommandMessage navigationAction(String label, String command, int page) {
        return CommandMessage.text(label, Color.WHITE).withInteraction(CommandMessage.clickRun(command), CommandMessage.text(format(CLICK_TO_OPEN, page), Color.GRAY));
    }

    private static CommandMessage append(CommandMessage current, CommandMessage next) {
        return current == null ? next : current.append(next);
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
