package me.clutchy.clutchperms.common.command;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.clutchy.clutchperms.common.audit.AuditEntry;
import me.clutchy.clutchperms.common.audit.AuditLogRecord;
import me.clutchy.clutchperms.common.audit.AuditLogRetention;
import me.clutchy.clutchperms.common.config.ClutchPermsAuditRetentionConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsBackupConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsBackupScheduleConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsChatConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsCommandConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsPaperConfig;
import me.clutchy.clutchperms.common.display.DisplayProfile;
import me.clutchy.clutchperms.common.display.DisplaySlot;
import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.subject.SubjectMetadata;

final class CommandAuditSupport<S> {

    private static final Duration DESTRUCTIVE_CONFIRMATION_TTL = Duration.ofSeconds(30);

    private static final Object CONSOLE_CONFIRMATION_SOURCE = new Object();

    private static final Map<ConfirmationSource, PendingConfirmation> PENDING_CONFIRMATIONS = new HashMap<>();

    private static final Gson GSON = new Gson();

    private static Clock confirmationClock = Clock.systemUTC();

    private final ClutchPermsCommandEnvironment<S> environment;

    private final CommandSupport<S> support;

    private final CommandFormatting<S> formatting;

    CommandAuditSupport(ClutchPermsCommandEnvironment<S> environment, CommandSupport<S> support, CommandFormatting<S> formatting) {
        this.environment = environment;
        this.support = support;
        this.formatting = formatting;
    }

    static void resetDestructiveConfirmationsForTests() {
        synchronized (PENDING_CONFIRMATIONS) {
            PENDING_CONFIRMATIONS.clear();
            confirmationClock = Clock.systemUTC();
        }
    }

    static void setConfirmationClockForTests(Clock clock) {
        synchronized (PENDING_CONFIRMATIONS) {
            confirmationClock = Objects.requireNonNull(clock, "clock");
            PENDING_CONFIRMATIONS.clear();
        }
    }

    boolean confirmDestructiveCommand(CommandContext<S> context, String operationKey) {
        ConfirmationSource source = confirmationSource(context.getSource());
        Instant now;
        synchronized (PENDING_CONFIRMATIONS) {
            now = confirmationClock.instant();
            PendingConfirmation pending = PENDING_CONFIRMATIONS.get(source);
            if (pending != null && pending.operationKey().equals(operationKey) && !now.isAfter(pending.expiresAt())) {
                PENDING_CONFIRMATIONS.remove(source);
                return true;
            }
            PENDING_CONFIRMATIONS.put(source, new PendingConfirmation(operationKey, now.plus(DESTRUCTIVE_CONFIRMATION_TTL)));
        }

        String command = confirmationCommand(context);
        environment.sendMessage(context.getSource(), CommandLang.confirmationRequired());
        environment.sendMessage(context.getSource(), CommandLang.confirmationRepeat(command, DESTRUCTIVE_CONFIRMATION_TTL.toSeconds()));
        return false;
    }

    String confirmationOperation(String action, String target) {
        return action + ":" + target;
    }

    void recordAudit(CommandContext<S> context, String action, String targetType, String targetKey, String targetDisplay, String beforeJson, String afterJson, boolean undoable) {
        try {
            environment.auditLogService()
                    .append(new AuditLogRecord(Instant.now(), environment.sourceKind(context.getSource()), environment.sourceSubjectId(context.getSource()),
                            actorName(context.getSource()), action, targetType, targetKey, targetDisplay, canonicalJson(beforeJson), canonicalJson(afterJson),
                            confirmationCommand(context), undoable));
        } catch (RuntimeException exception) {
            environment.sendMessage(context.getSource(), CommandLang.auditFailed(exception));
            return;
        }
        applyAutomaticAuditRetention(context.getSource());
    }

    void appendPruneAudit(CommandContext<S> context, String action, String targetKey, String targetDisplay, String beforeJson, String afterJson) throws CommandSyntaxException {
        try {
            environment.auditLogService()
                    .append(new AuditLogRecord(Instant.now(), environment.sourceKind(context.getSource()), environment.sourceSubjectId(context.getSource()),
                            actorName(context.getSource()), action, "audit", targetKey, targetDisplay, canonicalJson(beforeJson), canonicalJson(afterJson),
                            confirmationCommand(context), false));
        } catch (RuntimeException exception) {
            throw support.auditOperationFailed(exception);
        }
    }

    CommandPaging.PagedRow historyRow(String rootLiteral, AuditEntry entry) {
        String status = entry.undone() ? " undone" : "";
        String text = "#" + entry.id() + " " + entry.timestamp() + " " + entry.action() + " " + entry.targetDisplay() + " by " + formatAuditActor(entry) + status;
        return new CommandPaging.PagedRow(text, formatting.fullCommand(rootLiteral, "undo " + entry.id()));
    }

    int undoAuditEntry(CommandContext<S> context) throws CommandSyntaxException {
        long id = LongArgumentType.getLong(context, "id");
        AuditEntry entry = environment.auditLogService().get(id).orElseThrow(() -> support.feedback(List.of(CommandLang.auditMissing(id))));
        if (!entry.undoable()) {
            throw support.feedback(List.of(CommandLang.auditNotUndoable(id)));
        }
        if (entry.undone()) {
            throw support.feedback(List.of(CommandLang.auditAlreadyUndone(id)));
        }

        String currentJson = currentUndoSnapshot(entry);
        if (!canonicalJson(currentJson).equals(canonicalJson(entry.afterJson()))) {
            throw support.feedback(List.of(CommandLang.auditConflict(id)));
        }

        applyUndoSnapshot(entry);
        AuditEntry undoEntry;
        try {
            undoEntry = environment.auditLogService()
                    .append(new AuditLogRecord(Instant.now(), environment.sourceKind(context.getSource()), environment.sourceSubjectId(context.getSource()),
                            actorName(context.getSource()), "undo", entry.targetType(), entry.targetKey(), "undo #" + entry.id() + " " + entry.targetDisplay(),
                            canonicalJson(entry.afterJson()), canonicalJson(entry.beforeJson()), confirmationCommand(context), false));
            environment.auditLogService().markUndone(entry.id(), undoEntry.id(), undoEntry.timestamp(), environment.sourceSubjectId(context.getSource()),
                    actorName(context.getSource()));
        } catch (RuntimeException exception) {
            environment.sendMessage(context.getSource(), CommandLang.auditFailed(exception));
            undoEntry = null;
        }
        if (undoEntry != null) {
            applyAutomaticAuditRetention(context.getSource());
        }
        environment.refreshRuntimePermissions();
        environment.sendMessage(context.getSource(), CommandLang.auditUndone(id));
        return Command.SINGLE_SUCCESS;
    }

    private void applyAutomaticAuditRetention(S source) {
        try {
            AuditLogRetention.apply(environment.config(), environment.auditLogService(), Clock.systemUTC());
        } catch (RuntimeException exception) {
            environment.sendMessage(source, CommandLang.auditRetentionFailed(exception));
        }
    }

    private Optional<String> actorName(S source) {
        if (environment.sourceKind(source) == CommandSourceKind.PLAYER) {
            return environment.sourceSubjectId(source).flatMap(subjectId -> environment.subjectMetadataService().getSubject(subjectId).map(SubjectMetadata::lastKnownName));
        }
        if (environment.sourceKind(source) == CommandSourceKind.CONSOLE) {
            return Optional.of("console");
        }
        return Optional.empty();
    }

    private ConfirmationSource confirmationSource(S source) {
        if (environment.sourceKind(source) == CommandSourceKind.PLAYER) {
            return new ConfirmationSource(environment.sourceSubjectId(source).orElseThrow(() -> new IllegalStateException("player command source has no subject id")));
        }
        return new ConfirmationSource(CONSOLE_CONFIRMATION_SOURCE);
    }

    private String confirmationCommand(CommandContext<S> context) {
        String input = context.getInput().trim();
        return input.startsWith("/") ? input : "/" + input;
    }

    private String formatAuditActor(AuditEntry entry) {
        return entry.actorName().or(() -> entry.actorId().map(UUID::toString)).orElse(entry.actorKind().name().toLowerCase(java.util.Locale.ROOT));
    }

    private String currentUndoSnapshot(AuditEntry entry) {
        return switch (entry.targetType()) {
            case "user-permissions" -> subjectPermissionsSnapshot(UUID.fromString(entry.targetKey()));
            case "user-display" -> subjectDisplaySnapshot(UUID.fromString(entry.targetKey()));
            case "user-groups" -> subjectMembershipSnapshot(UUID.fromString(entry.targetKey()));
            case "group" -> groupSnapshot(entry.targetKey());
            case "group-rename" -> renameSnapshotForEntry(entry);
            case "group-permissions" -> groupPermissionsSnapshot(entry.targetKey());
            case "group-display" -> groupDisplaySnapshot(entry.targetKey());
            case "track" -> trackSnapshot(entry.targetKey());
            case "track-rename" -> renameTrackSnapshotForEntry(entry);
            case "config" -> configSnapshot(environment.config());
            default -> throw new IllegalArgumentException("unsupported undo target type: " + entry.targetType());
        };
    }

    private void applyUndoSnapshot(AuditEntry entry) {
        switch (entry.targetType()) {
            case "user-permissions" -> applySubjectPermissionsSnapshot(UUID.fromString(entry.targetKey()), entry.beforeJson());
            case "user-display" -> applySubjectDisplaySnapshot(UUID.fromString(entry.targetKey()), entry.beforeJson());
            case "user-groups" -> applySubjectMembershipSnapshot(UUID.fromString(entry.targetKey()), entry.beforeJson());
            case "group" -> applyGroupSnapshot(entry.targetKey(), entry.beforeJson());
            case "group-rename" -> applyRenameSnapshot(entry.beforeJson());
            case "group-permissions" -> applyGroupPermissionsSnapshot(entry.targetKey(), entry.beforeJson());
            case "group-display" -> applyGroupDisplaySnapshot(entry.targetKey(), entry.beforeJson());
            case "track" -> applyTrackSnapshot(entry.targetKey(), entry.beforeJson());
            case "track-rename" -> applyRenameTrackSnapshot(entry.beforeJson());
            case "config" -> applyConfigSnapshot(entry.beforeJson());
            default -> throw new IllegalArgumentException("unsupported undo target type: " + entry.targetType());
        }
    }

    String subjectPermissionsSnapshot(UUID subjectId) {
        JsonObject root = new JsonObject();
        JsonObject permissions = new JsonObject();
        new TreeMap<>(environment.permissionService().getPermissions(subjectId)).forEach((node, value) -> permissions.addProperty(node, value.name()));
        root.add("permissions", permissions);
        return GSON.toJson(root);
    }

    private void applySubjectPermissionsSnapshot(UUID subjectId, String snapshotJson) {
        JsonObject permissions = JsonParser.parseString(snapshotJson).getAsJsonObject().getAsJsonObject("permissions");
        environment.permissionService().clearPermissions(subjectId);
        permissions.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> environment.permissionService().setPermission(subjectId, entry.getKey(), PermissionValue.valueOf(entry.getValue().getAsString())));
    }

    String subjectDisplaySnapshot(UUID subjectId) {
        return displaySnapshot(environment.subjectMetadataService().getSubjectDisplay(subjectId));
    }

    private void applySubjectDisplaySnapshot(UUID subjectId, String snapshotJson) {
        JsonObject root = JsonParser.parseString(snapshotJson).getAsJsonObject();
        applySubjectDisplayValue(subjectId, DisplaySlot.PREFIX, root.get("prefix"));
        applySubjectDisplayValue(subjectId, DisplaySlot.SUFFIX, root.get("suffix"));
    }

    private void applySubjectDisplayValue(UUID subjectId, DisplaySlot slot, JsonElement value) {
        if (value == null || value.isJsonNull()) {
            if (slot == DisplaySlot.PREFIX) {
                environment.subjectMetadataService().clearSubjectPrefix(subjectId);
            } else {
                environment.subjectMetadataService().clearSubjectSuffix(subjectId);
            }
            return;
        }
        DisplayText displayText = DisplayText.parse(value.getAsString());
        if (slot == DisplaySlot.PREFIX) {
            environment.subjectMetadataService().setSubjectPrefix(subjectId, displayText);
        } else {
            environment.subjectMetadataService().setSubjectSuffix(subjectId, displayText);
        }
    }

    String subjectMembershipSnapshot(UUID subjectId) {
        JsonObject root = new JsonObject();
        JsonArray groups = new JsonArray();
        environment.groupService().getSubjectGroups(subjectId).stream().sorted().forEach(groups::add);
        root.add("groups", groups);
        return GSON.toJson(root);
    }

    private void applySubjectMembershipSnapshot(UUID subjectId, String snapshotJson) {
        Set<String> desiredGroups = stringSet(JsonParser.parseString(snapshotJson).getAsJsonObject().getAsJsonArray("groups"));
        environment.groupService().setSubjectGroups(subjectId, desiredGroups);
    }

    String groupSnapshot(String groupName) {
        String normalizedGroupName = formatting.normalizeGroupName(groupName);
        JsonObject root = new JsonObject();
        root.addProperty("name", normalizedGroupName);
        boolean exists = environment.groupService().hasGroup(normalizedGroupName);
        root.addProperty("exists", exists);
        if (!exists) {
            return GSON.toJson(root);
        }
        root.add("permissions", permissionsJson(environment.groupService().getGroupPermissions(normalizedGroupName)));
        root.add("display", JsonParser.parseString(groupDisplaySnapshot(normalizedGroupName)));
        root.add("parents", stringArray(environment.groupService().getGroupParents(normalizedGroupName)));
        root.add("members", uuidArray(environment.groupService().getGroupMembers(normalizedGroupName)));
        root.add("children", stringArray(formatting.findChildGroups(normalizedGroupName)));
        root.add("tracks", formatting.groupTrackReferencesJson(normalizedGroupName));
        return GSON.toJson(root);
    }

    private void applyGroupSnapshot(String groupName, String snapshotJson) {
        JsonObject root = JsonParser.parseString(snapshotJson).getAsJsonObject();
        String normalizedGroupName = root.get("name").getAsString();
        if (!root.get("exists").getAsBoolean()) {
            if (environment.groupService().hasGroup(normalizedGroupName)) {
                environment.groupService().deleteGroup(normalizedGroupName);
            }
            return;
        }
        if (!environment.groupService().hasGroup(normalizedGroupName)) {
            environment.groupService().createGroup(normalizedGroupName);
        }
        applyGroupPermissionsSnapshot(normalizedGroupName, objectWith("permissions", root.getAsJsonObject("permissions")));
        applyGroupDisplaySnapshot(normalizedGroupName, root.getAsJsonObject("display").toString());
        applyGroupParents(normalizedGroupName, stringSet(root.getAsJsonArray("parents")));
        applyGroupMembers(normalizedGroupName, uuidSet(root.getAsJsonArray("members")));
        formatting.applyGroupTracks(normalizedGroupName, formatting.trackReferencePositions(root.getAsJsonArray("tracks")));
        for (String child : stringSet(root.getAsJsonArray("children"))) {
            if (environment.groupService().hasGroup(child) && !environment.groupService().getGroupParents(child).contains(normalizedGroupName)) {
                environment.groupService().addGroupParent(child, normalizedGroupName);
            }
        }
    }

    String groupPermissionsSnapshot(String groupName) {
        return objectWith("permissions", permissionsJson(environment.groupService().getGroupPermissions(groupName)));
    }

    private void applyGroupPermissionsSnapshot(String groupName, String snapshotJson) {
        JsonObject permissions = JsonParser.parseString(snapshotJson).getAsJsonObject().getAsJsonObject("permissions");
        environment.groupService().clearGroupPermissions(groupName);
        permissions.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> environment.groupService().setGroupPermission(groupName, entry.getKey(), PermissionValue.valueOf(entry.getValue().getAsString())));
    }

    String groupDisplaySnapshot(String groupName) {
        return displaySnapshot(environment.groupService().getGroupDisplay(groupName));
    }

    private void applyGroupDisplaySnapshot(String groupName, String snapshotJson) {
        JsonObject root = JsonParser.parseString(snapshotJson).getAsJsonObject();
        applyGroupDisplayValue(groupName, DisplaySlot.PREFIX, root.get("prefix"));
        applyGroupDisplayValue(groupName, DisplaySlot.SUFFIX, root.get("suffix"));
    }

    private void applyGroupDisplayValue(String groupName, DisplaySlot slot, JsonElement value) {
        if (value == null || value.isJsonNull()) {
            if (slot == DisplaySlot.PREFIX) {
                environment.groupService().clearGroupPrefix(groupName);
            } else {
                environment.groupService().clearGroupSuffix(groupName);
            }
            return;
        }
        DisplayText displayText = DisplayText.parse(value.getAsString());
        if (slot == DisplaySlot.PREFIX) {
            environment.groupService().setGroupPrefix(groupName, displayText);
        } else {
            environment.groupService().setGroupSuffix(groupName, displayText);
        }
    }

    private void applyGroupParents(String groupName, Set<String> desiredParents) {
        Set<String> currentParents = environment.groupService().getGroupParents(groupName);
        currentParents.stream().filter(parent -> !desiredParents.contains(parent)).toList().forEach(parent -> environment.groupService().removeGroupParent(groupName, parent));
        desiredParents.stream().filter(parent -> !currentParents.contains(parent)).forEach(parent -> environment.groupService().addGroupParent(groupName, parent));
    }

    private void applyGroupMembers(String groupName, Set<UUID> desiredMembers) {
        Set<UUID> currentMembers = environment.groupService().getGroupMembers(groupName);
        currentMembers.stream().filter(member -> !desiredMembers.contains(member)).toList().forEach(member -> environment.groupService().removeSubjectGroup(member, groupName));
        desiredMembers.stream().filter(member -> !currentMembers.contains(member)).forEach(member -> environment.groupService().addSubjectGroup(member, groupName));
    }

    String trackSnapshot(String trackName) {
        String normalizedTrackName = formatting.normalizeTrackName(trackName);
        JsonObject root = new JsonObject();
        root.addProperty("name", normalizedTrackName);
        boolean exists = environment.trackService().hasTrack(normalizedTrackName);
        root.addProperty("exists", exists);
        if (!exists) {
            return GSON.toJson(root);
        }
        root.add("groups", orderedStringArray(environment.trackService().getTrackGroups(normalizedTrackName)));
        return GSON.toJson(root);
    }

    private void applyTrackSnapshot(String trackName, String snapshotJson) {
        JsonObject root = JsonParser.parseString(snapshotJson).getAsJsonObject();
        String normalizedTrackName = root.get("name").getAsString();
        if (!root.get("exists").getAsBoolean()) {
            if (environment.trackService().hasTrack(normalizedTrackName)) {
                environment.trackService().deleteTrack(normalizedTrackName);
            }
            return;
        }
        if (!environment.trackService().hasTrack(normalizedTrackName)) {
            environment.trackService().createTrack(normalizedTrackName);
        }
        environment.trackService().setTrackGroups(normalizedTrackName, stringList(root.getAsJsonArray("groups")));
    }

    String renameTrackSnapshot(String trackName, String newTrackName) {
        JsonObject root = new JsonObject();
        root.addProperty("oldName", formatting.normalizeTrackName(trackName));
        root.addProperty("newName", formatting.normalizeTrackName(newTrackName));
        root.add("old", JsonParser.parseString(trackSnapshot(trackName)));
        root.add("new", JsonParser.parseString(trackSnapshot(newTrackName)));
        return GSON.toJson(root);
    }

    private String renameTrackSnapshotForEntry(AuditEntry entry) {
        JsonObject before = JsonParser.parseString(entry.beforeJson()).getAsJsonObject();
        return renameTrackSnapshot(before.get("oldName").getAsString(), before.get("newName").getAsString());
    }

    private void applyRenameTrackSnapshot(String snapshotJson) {
        JsonObject root = JsonParser.parseString(snapshotJson).getAsJsonObject();
        applyTrackSnapshot(root.get("newName").getAsString(), root.getAsJsonObject("new").toString());
        applyTrackSnapshot(root.get("oldName").getAsString(), root.getAsJsonObject("old").toString());
    }

    String renameGroupSnapshot(String groupName, String newGroupName) {
        JsonObject root = new JsonObject();
        root.addProperty("oldName", formatting.normalizeGroupName(groupName));
        root.addProperty("newName", formatting.normalizeGroupName(newGroupName));
        root.add("old", JsonParser.parseString(groupSnapshot(groupName)));
        root.add("new", JsonParser.parseString(groupSnapshot(newGroupName)));
        return GSON.toJson(root);
    }

    private String renameSnapshotForEntry(AuditEntry entry) {
        JsonObject before = JsonParser.parseString(entry.beforeJson()).getAsJsonObject();
        return renameGroupSnapshot(before.get("oldName").getAsString(), before.get("newName").getAsString());
    }

    private void applyRenameSnapshot(String snapshotJson) {
        JsonObject root = JsonParser.parseString(snapshotJson).getAsJsonObject();
        applyGroupSnapshot(root.get("newName").getAsString(), root.getAsJsonObject("new").toString());
        applyGroupSnapshot(root.get("oldName").getAsString(), root.getAsJsonObject("old").toString());
    }

    String configSnapshot(ClutchPermsConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty("backups.retentionLimit", config.backups().retentionLimit());
        root.addProperty("backups.schedule.enabled", config.backups().schedule().enabled());
        root.addProperty("backups.schedule.intervalMinutes", config.backups().schedule().intervalMinutes());
        root.addProperty("backups.schedule.runOnStartup", config.backups().schedule().runOnStartup());
        root.addProperty("audit.retention.enabled", config.audit().enabled());
        root.addProperty("audit.retention.maxAgeDays", config.audit().maxAgeDays());
        root.addProperty("audit.retention.maxEntries", config.audit().maxEntries());
        root.addProperty("commands.helpPageSize", config.commands().helpPageSize());
        root.addProperty("commands.resultPageSize", config.commands().resultPageSize());
        root.addProperty("chat.enabled", config.chat().enabled());
        root.addProperty("paper.replaceOpCommands", config.paper().replaceOpCommands());
        return GSON.toJson(root);
    }

    private void applyConfigSnapshot(String snapshotJson) {
        JsonObject root = JsonParser.parseString(snapshotJson).getAsJsonObject();
        ClutchPermsBackupScheduleConfig defaultSchedule = ClutchPermsBackupScheduleConfig.defaults();
        ClutchPermsAuditRetentionConfig defaultAuditRetention = ClutchPermsAuditRetentionConfig.defaults();
        ClutchPermsConfig restoredConfig = new ClutchPermsConfig(
                new ClutchPermsBackupConfig(root.get("backups.retentionLimit").getAsInt(),
                        new ClutchPermsBackupScheduleConfig(booleanSnapshotValue(root, "backups.schedule.enabled", defaultSchedule.enabled()),
                                integerSnapshotValue(root, "backups.schedule.intervalMinutes", defaultSchedule.intervalMinutes()),
                                booleanSnapshotValue(root, "backups.schedule.runOnStartup", defaultSchedule.runOnStartup()))),
                new ClutchPermsAuditRetentionConfig(booleanSnapshotValue(root, "audit.retention.enabled", defaultAuditRetention.enabled()),
                        integerSnapshotValue(root, "audit.retention.maxAgeDays", defaultAuditRetention.maxAgeDays()),
                        integerSnapshotValue(root, "audit.retention.maxEntries", defaultAuditRetention.maxEntries())),
                new ClutchPermsCommandConfig(root.get("commands.helpPageSize").getAsInt(), root.get("commands.resultPageSize").getAsInt()),
                new ClutchPermsChatConfig(root.get("chat.enabled").getAsBoolean()), new ClutchPermsPaperConfig(root.get("paper.replaceOpCommands").getAsBoolean()));
        environment.updateConfig(ignored -> restoredConfig);
    }

    private boolean booleanSnapshotValue(JsonObject root, String key, boolean defaultValue) {
        return root.has(key) ? root.get(key).getAsBoolean() : defaultValue;
    }

    private int integerSnapshotValue(JsonObject root, String key, int defaultValue) {
        return root.has(key) ? root.get(key).getAsInt() : defaultValue;
    }

    private JsonObject permissionsJson(Map<String, PermissionValue> permissions) {
        JsonObject object = new JsonObject();
        new TreeMap<>(permissions).forEach((node, value) -> object.addProperty(node, value.name()));
        return object;
    }

    private String displaySnapshot(DisplayProfile display) {
        JsonObject root = new JsonObject();
        display.prefix().ifPresentOrElse(value -> root.addProperty("prefix", value.rawText()), () -> root.add("prefix", com.google.gson.JsonNull.INSTANCE));
        display.suffix().ifPresentOrElse(value -> root.addProperty("suffix", value.rawText()), () -> root.add("suffix", com.google.gson.JsonNull.INSTANCE));
        return GSON.toJson(root);
    }

    private JsonArray stringArray(Collection<String> values) {
        JsonArray array = new JsonArray();
        values.stream().sorted().forEach(array::add);
        return array;
    }

    private JsonArray orderedStringArray(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private JsonArray uuidArray(Collection<UUID> values) {
        JsonArray array = new JsonArray();
        values.stream().map(UUID::toString).sorted().forEach(array::add);
        return array;
    }

    private Set<String> stringSet(JsonArray array) {
        Set<String> values = new LinkedHashSet<>();
        array.forEach(value -> values.add(value.getAsString()));
        return values;
    }

    private List<String> stringList(JsonArray array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.getAsString()));
        return values;
    }

    private Set<UUID> uuidSet(JsonArray array) {
        Set<UUID> values = new LinkedHashSet<>();
        array.forEach(value -> values.add(UUID.fromString(value.getAsString())));
        return values;
    }

    private String objectWith(String key, JsonElement value) {
        JsonObject root = new JsonObject();
        root.add(key, value);
        return GSON.toJson(root);
    }

    private String canonicalJson(String json) {
        return GSON.toJson(JsonParser.parseString(json));
    }

    private record ConfirmationSource(Object key) {
    }

    private record PendingConfirmation(String operationKey, Instant expiresAt) {
    }
}
