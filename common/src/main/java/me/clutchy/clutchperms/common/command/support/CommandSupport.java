package me.clutchy.clutchperms.common.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.node.KnownPermissionNode;
import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.StorageBackup;
import me.clutchy.clutchperms.common.storage.StorageFileKind;

final class CommandSupport<S> {

    private static final SimpleCommandExceptionType FEEDBACK_MESSAGES = new SimpleCommandExceptionType(new LiteralMessage("command feedback"));

    private static final DynamicCommandExceptionType INVALID_NODE = new DynamicCommandExceptionType(node -> new LiteralMessage(CommandLang.invalidNode(node).plainText()));

    private static final DynamicCommandExceptionType INVALID_VALUE = new DynamicCommandExceptionType(value -> new LiteralMessage(CommandLang.invalidValue(value).plainText()));

    private static final DynamicCommandExceptionType RELOAD_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType VALIDATE_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType PERMISSION_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType GROUP_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType NODE_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType TRACK_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType BACKUP_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType CONFIG_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType DISPLAY_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType AUDIT_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private final ClutchPermsCommandEnvironment<S> environment;

    private final String rootLiteral;

    private final CommandFormatting<S> formatting;

    private final CommandTargetResolver<S> targets;

    private final CommandPaging<S> paging;

    private final CommandAuditSupport<S> audit;

    CommandSupport(ClutchPermsCommandEnvironment<S> environment, String rootLiteral) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.rootLiteral = Objects.requireNonNull(rootLiteral, "rootLiteral");
        this.formatting = new CommandFormatting<>(environment);
        this.targets = new CommandTargetResolver<>(environment, this, formatting);
        this.paging = new CommandPaging<>(environment, this);
        this.audit = new CommandAuditSupport<>(environment, this, formatting);
    }

    ClutchPermsCommandEnvironment<S> environment() {
        return environment;
    }

    String rootLiteral() {
        return rootLiteral;
    }

    CommandFormatting<S> formatting() {
        return formatting;
    }

    CommandTargetResolver<S> targets() {
        return targets;
    }

    CommandPaging<S> paging() {
        return paging;
    }

    CommandAuditSupport<S> audit() {
        return audit;
    }

    boolean canUse(S source, String requiredPermission) {
        CommandSourceKind sourceKind = environment.sourceKind(source);
        if (sourceKind == CommandSourceKind.CONSOLE) {
            return true;
        }
        if (sourceKind != CommandSourceKind.PLAYER) {
            return false;
        }

        Optional<UUID> subjectId = environment.sourceSubjectId(source);
        return subjectId.isPresent() && environment.permissionResolver().hasPermission(subjectId.get(), requiredPermission);
    }

    boolean canUseAny(S source, String... requiredPermissions) {
        for (String requiredPermission : requiredPermissions) {
            if (canUse(source, requiredPermission)) {
                return true;
            }
        }
        return false;
    }

    int executeAuthorized(CommandContext<S> context, String requiredPermission, CommandAction<S> action) throws CommandSyntaxException {
        S source = context.getSource();
        if (!canUse(source, requiredPermission)) {
            if (environment.sourceKind(source) == CommandSourceKind.OTHER) {
                environment.sendMessage(source, CommandLang.error(CommandLang.ERROR_OTHER_SOURCE_DENIED));
                return 0;
            }
            environment.sendMessage(source, CommandLang.error(CommandLang.ERROR_NO_PERMISSION));
            return 0;
        }

        return runActionWithFeedback(source, action);
    }

    int executeAuthorizedForSubject(CommandContext<S> context, String requiredPermission, SubjectCommandAction<S> action) throws CommandSyntaxException {
        return executeAuthorized(context, requiredPermission, source -> {
            String target = StringArgumentType.getString(context, CommandArguments.TARGET);
            Optional<CommandTargetResolver.ResolvedSubject> resolvedSubject = targets.resolveImmediateSubject(context, target);
            if (resolvedSubject.isPresent()) {
                CommandTargetResolver.ResolvedSubject subject = resolvedSubject.get();
                if (subject.recordMetadata()) {
                    targets.recordResolvedSubject(subject.subject());
                }
                return action.run(source, subject.subject());
            }

            CompletableFuture<Optional<CommandSubject>> lookupFuture;
            try {
                lookupFuture = Objects.requireNonNull(environment.resolveSubjectAsync(source, target), "lookupFuture");
            } catch (RuntimeException exception) {
                throw feedback(List.of(CommandLang.userTargetLookupFailed(target, exception)));
            }

            if (lookupFuture.isDone()) {
                final Optional<CommandSubject> completedSubject;
                try {
                    completedSubject = lookupFuture.join();
                } catch (RuntimeException exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    throw feedback(List.of(CommandLang.userTargetLookupFailed(target, cause)));
                }
                if (completedSubject.isEmpty()) {
                    throw targets.unknownUserTargetFeedback(context, target);
                }

                CommandSubject subject = completedSubject.get();
                targets.recordResolvedSubject(subject);
                environment.sendMessage(source, CommandLang.userTargetLookupResolved(target, formatting.formatSubject(subject)));
                return action.run(source, subject);
            }

            environment.sendMessage(source, CommandLang.userTargetLookupQueued(target));
            lookupFuture.whenComplete(
                    (resolved, failure) -> environment.executeOnCommandThread(source, () -> targets.resumeResolvedSubject(context, source, target, action, resolved, failure)));
            return Command.SINGLE_SUCCESS;
        });
    }

    int runActionWithFeedback(S source, CommandAction<S> action) {
        try {
            return action.run(source);
        } catch (CommandFeedbackException exception) {
            exception.messages().forEach(message -> environment.sendMessage(source, message));
            return 0;
        } catch (CommandSyntaxException exception) {
            environment.sendMessage(source, CommandLang.error(exception.getRawMessage().getString()));
            return 0;
        }
    }

    int sendUsage(CommandContext<S> context, String error, String detail, List<String> usages) {
        S source = context.getSource();
        environment.sendMessage(source, CommandLang.error(error));
        environment.sendMessage(source, CommandLang.detail(detail));
        environment.sendMessage(source, CommandLang.tryHeader());
        String currentRootLiteral = currentRootLiteral(context);
        usages.forEach(usage -> environment.sendMessage(source, CommandLang.suggestion(currentRootLiteral, usage)));
        return 0;
    }

    boolean hasArgument(CommandContext<S> context, String argumentName) {
        return context.getNodes().stream().anyMatch(node -> argumentName.equals(node.getNode().getName()));
    }

    String currentRootLiteral(CommandContext<S> context) {
        if (context.getNodes().isEmpty()) {
            return rootLiteral;
        }
        return context.getNodes().getFirst().getNode().getName();
    }

    String currentFullCommand(CommandContext<S> context, String command) {
        return formatting.fullCommand(currentRootLiteral(context), command);
    }

    CommandCatalogs.ConfigEntry getConfigEntry(CommandContext<S> context) throws CommandSyntaxException {
        String key = StringArgumentType.getString(context, CommandArguments.CONFIG_KEY);
        return CommandCatalogs.findConfigEntry(key).orElseThrow(() -> unknownConfigKeyFeedback(context, key));
    }

    String parseConfigValue(CommandContext<S> context, CommandCatalogs.ConfigEntry entry, String rawValue) throws CommandSyntaxException {
        try {
            return entry.normalizeValue(rawValue);
        } catch (IllegalArgumentException exception) {
            throw invalidConfigValueFeedback(context, entry, rawValue);
        }
    }

    String getNode(CommandContext<S> context) throws CommandSyntaxException {
        String node = StringArgumentType.getString(context, CommandArguments.NODE);
        return validateNode(node);
    }

    PermissionAssignment getPermissionAssignment(CommandContext<S> context) throws CommandSyntaxException {
        String assignment = StringArgumentType.getString(context, CommandArguments.ASSIGNMENT).trim();
        int valueStart = lastWhitespaceRunStart(assignment);
        if (valueStart <= 0) {
            throw INVALID_VALUE.create(assignment);
        }

        String node = validateNode(assignment.substring(0, valueStart).trim());
        String rawValue = assignment.substring(valueStart).trim();
        PermissionValue value;
        if ("true".equalsIgnoreCase(rawValue)) {
            value = PermissionValue.TRUE;
        } else if ("false".equalsIgnoreCase(rawValue)) {
            value = PermissionValue.FALSE;
        } else {
            throw INVALID_VALUE.create(rawValue);
        }

        return new PermissionAssignment(node, value);
    }

    DisplayText getDisplayText(CommandContext<S> context) throws CommandSyntaxException {
        String rawDisplayText = StringArgumentType.getString(context, CommandArguments.DISPLAY_VALUE);
        try {
            return DisplayText.parse(rawDisplayText);
        } catch (IllegalArgumentException exception) {
            throw displayOperationFailed(exception);
        }
    }

    String getGroupName(CommandContext<S> context) throws CommandSyntaxException {
        String groupName = StringArgumentType.getString(context, CommandArguments.GROUP);
        if (groupName.trim().isEmpty()) {
            throw groupOperationFailed(new IllegalArgumentException("group name must not be blank"));
        }
        return groupName;
    }

    String getParentGroupName(CommandContext<S> context) throws CommandSyntaxException {
        String parentGroupName = StringArgumentType.getString(context, CommandArguments.PARENT);
        if (parentGroupName.trim().isEmpty()) {
            throw groupOperationFailed(new IllegalArgumentException("group name must not be blank"));
        }
        return parentGroupName;
    }

    String getTrackName(CommandContext<S> context) throws CommandSyntaxException {
        String trackName = StringArgumentType.getString(context, CommandArguments.TRACK);
        if (trackName.trim().isEmpty()) {
            throw trackOperationFailed(new IllegalArgumentException("track name must not be blank"));
        }
        return trackName;
    }

    String getNewGroupName(CommandContext<S> context) throws CommandSyntaxException {
        String newGroupName = StringArgumentType.getString(context, CommandArguments.NEW_GROUP);
        if (newGroupName.trim().isEmpty()) {
            throw groupOperationFailed(new IllegalArgumentException("group name must not be blank"));
        }
        return newGroupName;
    }

    String getNewTrackName(CommandContext<S> context) throws CommandSyntaxException {
        String newTrackName = StringArgumentType.getString(context, CommandArguments.NEW_TRACK);
        if (newTrackName.trim().isEmpty()) {
            throw trackOperationFailed(new IllegalArgumentException("track name must not be blank"));
        }
        return newTrackName;
    }

    int getTrackPosition(CommandContext<S> context) {
        return IntegerArgumentType.getInteger(context, CommandArguments.POSITION);
    }

    String requireExistingGroup(CommandContext<S> context, String groupName) throws CommandSyntaxException {
        String normalizedGroupName = formatting.normalizeGroupName(groupName);
        if (!environment.groupService().hasGroup(normalizedGroupName)) {
            throw unknownGroupTargetFeedback(context, normalizedGroupName, false);
        }
        return normalizedGroupName;
    }

    String requireExistingParentGroup(CommandContext<S> context, String groupName) throws CommandSyntaxException {
        String normalizedGroupName = formatting.normalizeGroupName(groupName);
        if (!environment.groupService().hasGroup(normalizedGroupName)) {
            throw unknownGroupTargetFeedback(context, normalizedGroupName, true);
        }
        return normalizedGroupName;
    }

    String requireExistingTrack(CommandContext<S> context, String trackName) throws CommandSyntaxException {
        String normalizedTrackName = formatting.normalizeTrackName(trackName);
        if (!environment.trackService().hasTrack(normalizedTrackName)) {
            throw unknownTrackTargetFeedback(context, normalizedTrackName);
        }
        return normalizedTrackName;
    }

    String requireTrackGroup(CommandContext<S> context, String trackName, String groupName) throws CommandSyntaxException {
        String normalizedGroupName = formatting.normalizeGroupName(groupName);
        if (!environment.trackService().getTrackGroups(trackName).contains(normalizedGroupName)) {
            throw trackOperationFailed(new IllegalArgumentException("group is not on track " + trackName + ": " + normalizedGroupName));
        }
        return normalizedGroupName;
    }

    StorageBackup requireKnownBackupFile(CommandContext<S> context, String backupFileName, List<StorageBackup> backups) throws CommandSyntaxException {
        Optional<StorageBackup> knownBackupFile = backups.stream().filter(backup -> backup.fileName().equals(backupFileName)).findFirst();
        if (knownBackupFile.isPresent()) {
            return knownBackupFile.get();
        }

        List<CommandMessage> messages = new ArrayList<>();
        messages.add(CommandLang.unknownBackupFile(backupFileName));
        List<String> matches = closestMatches(backupFileName, backups.stream().map(StorageBackup::fileName).toList());
        if (!matches.isEmpty()) {
            messages.add(CommandLang.closestBackupFiles(String.join(", ", matches)));
        }
        messages.add(CommandLang.tryHeader());
        messages.add(CommandLang.suggestion(currentRootLiteral(context), "backup list"));
        throw feedback(messages);
    }

    void validateBackup(StorageFileKind kind, StorageBackup backup) throws CommandSyntaxException {
        try {
            environment.validateBackup(kind, backup.path());
        } catch (RuntimeException exception) {
            throw backupOperationFailed(new PermissionStorageException("Failed to validate " + kind.token() + " backup " + backup.fileName(), exception));
        }
    }

    void requireManuallyRegisteredNode(CommandContext<S> context, String normalizedNode) throws CommandSyntaxException {
        if (environment.manualPermissionNodeRegistry().getKnownNode(normalizedNode).isPresent()) {
            return;
        }

        List<CommandMessage> messages = new ArrayList<>();
        Optional<KnownPermissionNode> knownNode = environment.permissionNodeRegistry().getKnownNode(normalizedNode);
        if (knownNode.isPresent()) {
            messages.add(CommandLang.nonManualNode(formatting.formatNodeSource(knownNode.get()), normalizedNode));
        } else {
            messages.add(CommandLang.unknownManualNode(normalizedNode));
        }
        messages.add(CommandLang.onlyManualNodesRemovable());
        List<KnownPermissionNode> manualNodes = environment.manualPermissionNodeRegistry().getKnownNodes().stream()
                .sorted(java.util.Comparator.comparing(KnownPermissionNode::node)).toList();
        if (manualNodes.isEmpty()) {
            messages.add(CommandLang.noManualNodes());
            messages.add(CommandLang.tryHeader());
            messages.add(CommandLang.suggestion(currentRootLiteral(context), "nodes add " + normalizedNode));
        } else {
            List<String> matches = closestMatches(normalizedNode, manualNodes.stream().map(KnownPermissionNode::node).toList());
            if (!matches.isEmpty()) {
                messages.add(CommandLang.closestManualNodes(String.join(", ", matches)));
            } else {
                messages.add(CommandLang.tryHeader());
                messages.add(CommandLang.suggestion(currentRootLiteral(context), "nodes list"));
            }
        }
        throw feedback(messages);
    }

    CompletableFuture<Suggestions> suggestPermissionAssignment(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int valueStart = lastWhitespaceRunStart(remaining);
        if (valueStart <= 0) {
            return suggestPermissionNodes(context, builder);
        }

        SuggestionsBuilder valueBuilder = builder.createOffset(builder.getStart() + valueStart);
        String partialValue = remaining.substring(valueStart).trim().toLowerCase(Locale.ROOT);
        if ("true".startsWith(partialValue)) {
            valueBuilder.suggest("true");
        }
        if ("false".startsWith(partialValue)) {
            valueBuilder.suggest("false");
        }
        return valueBuilder.buildFuture();
    }

    CompletableFuture<Suggestions> suggestPermissionNodes(CommandContext<S> context, SuggestionsBuilder builder) {
        Set<String> nodes = new LinkedHashSet<>();
        nodes.addAll(PermissionNodes.commandWildcardAssignments());
        environment.permissionNodeRegistry().getKnownNodes().stream().map(KnownPermissionNode::node).forEach(nodes::add);

        try {
            String groupName = StringArgumentType.getString(context, CommandArguments.GROUP);
            if (environment.groupService().hasGroup(groupName)) {
                nodes.addAll(environment.groupService().getGroupPermissions(groupName).keySet());
            }
        } catch (IllegalArgumentException exception) {
            // The current command may not have a group argument, so group-specific suggestions are best-effort.
        }

        try {
            targets.resolveSuggestionSubjectId(context).ifPresent(subjectId -> nodes.addAll(environment.permissionResolver().getEffectivePermissions(subjectId).keySet()));
        } catch (IllegalArgumentException exception) {
            // Target-specific node suggestions are best-effort when the target is incomplete or invalid.
        }

        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        nodes.stream().sorted().filter(node -> node.toLowerCase(Locale.ROOT).startsWith(remaining)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    CommandFeedbackException unknownGroupTargetFeedback(CommandContext<S> context, String groupName, boolean parentGroup) {
        List<CommandMessage> messages = new ArrayList<>();
        messages.add(parentGroup ? CommandLang.unknownParentGroupTarget(groupName) : CommandLang.unknownGroupTarget(groupName));

        Set<String> groups = environment.groupService().getGroups();
        if (groups.isEmpty()) {
            messages.add(CommandLang.noGroupsDefined());
            messages.add(CommandLang.tryHeader());
            messages.add(CommandLang.suggestion(currentRootLiteral(context), "group " + groupName + " create"));
            return feedback(messages);
        }

        List<String> matches = closestMatches(groupName, groups);
        if (!matches.isEmpty()) {
            messages.add(CommandLang.closestGroups(String.join(", ", matches)));
        } else {
            messages.add(CommandLang.noGroupTargetMatches());
            messages.add(CommandLang.tryHeader());
            messages.add(CommandLang.suggestion(currentRootLiteral(context), "group list"));
        }
        return feedback(messages);
    }

    CommandFeedbackException unknownTrackTargetFeedback(CommandContext<S> context, String trackName) {
        List<CommandMessage> messages = new ArrayList<>();
        messages.add(CommandLang.unknownTrackTarget(trackName));

        Set<String> tracks = environment.trackService().getTracks();
        if (tracks.isEmpty()) {
            messages.add(CommandLang.noTracksDefined());
            messages.add(CommandLang.tryHeader());
            messages.add(CommandLang.suggestion(currentRootLiteral(context), "track " + trackName + " create"));
            return feedback(messages);
        }

        List<String> matches = closestMatches(trackName, tracks);
        if (!matches.isEmpty()) {
            messages.add(CommandLang.closestTracks(String.join(", ", matches)));
        } else {
            messages.add(CommandLang.noTrackTargetMatches());
            messages.add(CommandLang.tryHeader());
            messages.add(CommandLang.suggestion(currentRootLiteral(context), "track list"));
        }
        return feedback(messages);
    }

    CommandFeedbackException unknownConfigKeyFeedback(CommandContext<S> context, String key) {
        List<CommandMessage> messages = new ArrayList<>();
        messages.add(CommandLang.unknownConfigKey(key));
        List<String> matches = closestMatches(key, CommandCatalogs.CONFIG_KEYS);
        if (matches.isEmpty()) {
            messages.add(CommandLang.validConfigKeys(String.join(", ", CommandCatalogs.CONFIG_KEYS)));
        } else {
            messages.add(CommandLang.closestConfigKeys(String.join(", ", matches)));
        }
        messages.add(CommandLang.tryHeader());
        messages.add(CommandLang.suggestion(currentRootLiteral(context), "config get <key>"));
        messages.add(CommandLang.suggestion(currentRootLiteral(context), "config list"));
        return feedback(messages);
    }

    CommandFeedbackException invalidConfigValueFeedback(CommandContext<S> context, CommandCatalogs.ConfigEntry entry, String rawValue) {
        return feedback(List.of(CommandLang.invalidConfigValue(entry.key(), rawValue), CommandLang.configValueRequirement(entry.key(), entry.errorHint()), CommandLang.tryHeader(),
                CommandLang.suggestion(currentRootLiteral(context), "config set " + entry.key() + " <value>")));
    }

    CommandFeedbackException feedback(List<CommandMessage> messages) {
        return new CommandFeedbackException(messages);
    }

    CommandSyntaxException reloadFailed(Throwable throwable) {
        return RELOAD_FAILED.create(CommandLang.reloadFailed(throwable));
    }

    CommandSyntaxException validateFailed(Throwable throwable) {
        return VALIDATE_FAILED.create(CommandLang.validateFailed(throwable));
    }

    CommandSyntaxException permissionOperationFailed(Throwable throwable) {
        return PERMISSION_OPERATION_FAILED.create(CommandLang.permissionOperationFailed(throwable));
    }

    CommandSyntaxException groupOperationFailed(Throwable throwable) {
        return GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(throwable));
    }

    CommandSyntaxException nodeOperationFailed(Throwable throwable) {
        return NODE_OPERATION_FAILED.create(CommandLang.nodeOperationFailed(throwable));
    }

    CommandSyntaxException trackOperationFailed(Throwable throwable) {
        return TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(throwable));
    }

    CommandSyntaxException backupOperationFailed(Throwable throwable) {
        return BACKUP_OPERATION_FAILED.create(CommandLang.backupOperationFailed(throwable));
    }

    CommandSyntaxException configOperationFailed(Throwable throwable) {
        return CONFIG_OPERATION_FAILED.create(CommandLang.configOperationFailed(throwable));
    }

    CommandSyntaxException displayOperationFailed(Throwable throwable) {
        return DISPLAY_OPERATION_FAILED.create(CommandLang.displayOperationFailed(throwable));
    }

    CommandSyntaxException auditOperationFailed(Throwable throwable) {
        return AUDIT_OPERATION_FAILED.create(CommandLang.auditFailed(throwable));
    }

    private String validateNode(String node) throws CommandSyntaxException {
        try {
            PermissionNodes.normalize(node);
        } catch (IllegalArgumentException exception) {
            throw INVALID_NODE.create(node);
        }
        return node;
    }

    private int lastWhitespaceRunStart(String value) {
        int index = value.length() - 1;
        while (index >= 0 && !Character.isWhitespace(value.charAt(index))) {
            index--;
        }
        while (index > 0 && Character.isWhitespace(value.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    private List<String> closestMatches(String target, Collection<String> candidates) {
        return candidates.stream().map(value -> new MatchCandidate(value, value))
                .collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(), list -> closestMatches(target, list)));
    }

    private List<String> closestMatches(String target, List<MatchCandidate> candidates) {
        String query = target.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return List.of();
        }

        return candidates
                .stream().map(candidate -> matchCandidate(query, candidate)).flatMap(Optional::stream).sorted(java.util.Comparator.comparingInt(TargetMatch::category)
                        .thenComparingInt(TargetMatch::score).thenComparing(TargetMatch::sortText).thenComparing(TargetMatch::displayText))
                .limit(5).map(TargetMatch::displayText).toList();
    }

    private Optional<TargetMatch> matchCandidate(String query, MatchCandidate candidate) {
        String candidateText = candidate.matchText().trim().toLowerCase(Locale.ROOT);
        if (candidateText.isEmpty()) {
            return Optional.empty();
        }
        if (candidateText.startsWith(query)) {
            return Optional.of(new TargetMatch(candidate.displayText(), 0, candidateText.length() - query.length(), candidateText));
        }
        int substringIndex = candidateText.indexOf(query);
        if (substringIndex >= 0) {
            return Optional.of(new TargetMatch(candidate.displayText(), 1, substringIndex, candidateText));
        }
        int reverseSubstringIndex = query.indexOf(candidateText);
        if (reverseSubstringIndex >= 0) {
            return Optional.of(new TargetMatch(candidate.displayText(), 1, 100 + reverseSubstringIndex, candidateText));
        }
        int maximumDistance = query.length() <= 3 ? 1 : query.length() <= 8 ? 2 : 3;
        int distance = editDistance(query, candidateText, maximumDistance);
        if (distance <= maximumDistance) {
            return Optional.of(new TargetMatch(candidate.displayText(), 2, distance, candidateText));
        }
        return Optional.empty();
    }

    private int editDistance(String first, String second, int cutoff) {
        if (Math.abs(first.length() - second.length()) > cutoff) {
            return cutoff + 1;
        }

        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];
        for (int column = 0; column <= second.length(); column++) {
            previous[column] = column;
        }

        for (int row = 1; row <= first.length(); row++) {
            current[0] = row;
            int bestInRow = current[0];
            for (int column = 1; column <= second.length(); column++) {
                int substitutionCost = first.charAt(row - 1) == second.charAt(column - 1) ? 0 : 1;
                int insertion = current[column - 1] + 1;
                int deletion = previous[column] + 1;
                int substitution = previous[column - 1] + substitutionCost;
                current[column] = Math.min(Math.min(insertion, deletion), substitution);
                bestInRow = Math.min(bestInRow, current[column]);
            }
            if (bestInRow > cutoff) {
                return cutoff + 1;
            }

            int[] nextPrevious = previous;
            previous = current;
            current = nextPrevious;
        }

        return previous[second.length()];
    }

    @FunctionalInterface
    interface CommandAction<S> {

        int run(S source) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface SubjectCommandAction<S> {

        int run(S source, CommandSubject subject) throws CommandSyntaxException;
    }

    static final class CommandFeedbackException extends CommandSyntaxException {

        private final List<CommandMessage> messages;

        CommandFeedbackException(List<CommandMessage> messages) {
            super(FEEDBACK_MESSAGES, FEEDBACK_MESSAGES.create().getRawMessage());
            this.messages = List.copyOf(messages);
        }

        List<CommandMessage> messages() {
            return messages;
        }
    }

    record PermissionAssignment(String node, PermissionValue value) {
    }

    private record MatchCandidate(String matchText, String displayText) {
    }

    private record TargetMatch(String displayText, int category, int score, String sortText) {
    }
}
