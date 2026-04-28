package me.clutchy.clutchperms.common.command;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import me.clutchy.clutchperms.common.subject.SubjectMetadata;

final class CommandTargetResolver<S> {

    private static final int TARGET_MATCH_LIMIT = 5;

    private static final Comparator<String> SUGGESTION_ORDER = Comparator.comparing((String value) -> value.toLowerCase(Locale.ROOT)).thenComparing(Comparator.naturalOrder());

    private final ClutchPermsCommandEnvironment<S> environment;

    private final CommandSupport<S> support;

    private final CommandFormatting<S> formatting;

    CommandTargetResolver(ClutchPermsCommandEnvironment<S> environment, CommandSupport<S> support, CommandFormatting<S> formatting) {
        this.environment = environment;
        this.support = support;
        this.formatting = formatting;
    }

    Optional<ResolvedSubject> resolveImmediateSubject(CommandContext<S> context, String target) throws CommandSyntaxException {
        Optional<CommandSubject> onlineSubject = environment.findOnlineSubject(context.getSource(), target);
        if (onlineSubject.isPresent()) {
            return onlineSubject.map(subject -> new ResolvedSubject(subject, false));
        }

        Optional<CommandSubject> cachedSubject = environment.findCachedSubject(context.getSource(), target);
        if (cachedSubject.isPresent()) {
            return cachedSubject.map(subject -> new ResolvedSubject(subject, true));
        }

        Optional<CommandSubject> knownSubject = resolveKnownSubject(context, target);
        if (knownSubject.isPresent()) {
            return knownSubject.map(subject -> new ResolvedSubject(subject, false));
        }

        try {
            UUID subjectId = UUID.fromString(target);
            String displayName = environment.subjectMetadataService().getSubject(subjectId).map(SubjectMetadata::lastKnownName).orElse(subjectId.toString());
            return Optional.of(new ResolvedSubject(new CommandSubject(subjectId, displayName), false));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    Optional<UUID> resolveSuggestionSubjectId(CommandContext<S> context) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        Optional<CommandSubject> onlineSubject = environment.findOnlineSubject(context.getSource(), target);
        if (onlineSubject.isPresent()) {
            return onlineSubject.map(CommandSubject::id);
        }

        Optional<CommandSubject> cachedSubject = environment.findCachedSubject(context.getSource(), target);
        if (cachedSubject.isPresent()) {
            return cachedSubject.map(CommandSubject::id);
        }

        List<SubjectMetadata> knownSubjects = findKnownSuggestionSubjects(target);
        if (knownSubjects.size() == 1) {
            return Optional.of(knownSubjects.getFirst().subjectId());
        }
        if (!knownSubjects.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(target));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    CompletableFuture<Suggestions> suggestUserTargets(S source, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        StringRange range = StringRange.between(builder.getStart(), builder.getInput().length());
        List<Suggestion> suggestions = Stream
                .concat(environment.onlineSubjectNames(source).stream(), environment.subjectMetadataService().getSubjects().values().stream().map(SubjectMetadata::lastKnownName))
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining)).sorted(SUGGESTION_ORDER).distinct().filter(name -> !name.equals(builder.getRemaining()))
                .map(name -> new Suggestion(range, name)).toList();
        return CompletableFuture.completedFuture(new Suggestions(range, suggestions));
    }

    CommandSupport.CommandFeedbackException unknownUserTargetFeedback(CommandContext<S> context, String target) {
        List<CommandMessage> messages = new ArrayList<>();
        messages.add(CommandLang.unknownUserTarget(target));
        messages.add(CommandLang.userTargetForms());

        List<String> onlineMatches = closestMatches(target, environment.onlineSubjectNames(context.getSource()).stream().map(CommandTargetResolver::candidate).toList());
        if (!onlineMatches.isEmpty()) {
            messages.add(CommandLang.closestOnlineUsers(String.join(", ", onlineMatches)));
        }

        List<String> knownMatches = closestMatches(target, environment.subjectMetadataService().getSubjects().values().stream()
                .map(subject -> candidate(subject.lastKnownName(), formatting.formatSubjectMetadata(subject))).toList());
        if (!knownMatches.isEmpty()) {
            messages.add(CommandLang.closestKnownUsers(String.join(", ", knownMatches)));
        }

        if (onlineMatches.isEmpty() && knownMatches.isEmpty()) {
            messages.add(CommandLang.noUserTargetMatches());
            messages.add(CommandLang.tryHeader());
            messages.add(CommandLang.suggestion(support.currentRootLiteral(context), "users search " + target));
        }
        return support.feedback(messages);
    }

    void recordResolvedSubject(CommandSubject subject) {
        environment.subjectMetadataService().recordSubject(subject.id(), subject.displayName(), Instant.now());
    }

    void resumeResolvedSubject(CommandContext<S> context, S source, String target, CommandSupport.SubjectCommandAction<S> action, Optional<CommandSubject> resolved,
            Throwable failure) {
        if (failure != null) {
            environment.sendMessage(source, CommandLang.userTargetLookupFailed(target, failure));
            return;
        }
        if (resolved == null || resolved.isEmpty()) {
            try {
                throw unknownUserTargetFeedback(context, target);
            } catch (CommandSupport.CommandFeedbackException exception) {
                exception.messages().forEach(message -> environment.sendMessage(source, message));
            }
            return;
        }

        CommandSubject subject = resolved.get();
        recordResolvedSubject(subject);
        environment.sendMessage(source, CommandLang.userTargetLookupResolved(target, formatting.formatSubject(subject)));
        support.runActionWithFeedback(source, ignored -> action.run(source, subject));
    }

    private Optional<CommandSubject> resolveKnownSubject(CommandContext<S> context, String target) throws CommandSyntaxException {
        String normalizedTarget = target.toLowerCase(Locale.ROOT);
        List<SubjectMetadata> matches = environment.subjectMetadataService().getSubjects().values().stream()
                .filter(subject -> subject.lastKnownName().toLowerCase(Locale.ROOT).equals(normalizedTarget))
                .sorted(Comparator.comparing(SubjectMetadata::lastKnownName, String.CASE_INSENSITIVE_ORDER).thenComparing(SubjectMetadata::subjectId)).toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() > 1) {
            throw ambiguousKnownUserFeedback(target, matches);
        }

        SubjectMetadata subject = matches.getFirst();
        return Optional.of(new CommandSubject(subject.subjectId(), subject.lastKnownName()));
    }

    private CommandSupport.CommandFeedbackException ambiguousKnownUserFeedback(String target, List<SubjectMetadata> matches) {
        List<CommandMessage> messages = new ArrayList<>();
        messages.add(CommandLang.ambiguousKnownUser(target));
        messages.add(CommandLang.ambiguousKnownUserDetail(target));
        matches.stream().map(formatting::formatSubjectMetadata).forEach(match -> messages.add(CommandLang.targetMatch(match)));
        return support.feedback(messages);
    }

    private List<SubjectMetadata> findKnownSuggestionSubjects(String target) {
        String normalizedTarget = target.toLowerCase(Locale.ROOT);
        return environment.subjectMetadataService().getSubjects().values().stream().filter(subject -> subject.lastKnownName().toLowerCase(Locale.ROOT).equals(normalizedTarget))
                .sorted(Comparator.comparing(SubjectMetadata::lastKnownName, String.CASE_INSENSITIVE_ORDER).thenComparing(SubjectMetadata::subjectId)).toList();
    }

    private static TargetCandidate candidate(String text) {
        return candidate(text, text);
    }

    private static TargetCandidate candidate(String matchText, String displayText) {
        return new TargetCandidate(matchText, displayText);
    }

    private static List<String> closestMatches(String target, Collection<TargetCandidate> candidates) {
        String query = target.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return List.of();
        }

        return candidates
                .stream().map(candidate -> matchCandidate(query, candidate)).flatMap(Optional::stream).sorted(Comparator.comparingInt(TargetMatch::category)
                        .thenComparingInt(TargetMatch::score).thenComparing(TargetMatch::sortText).thenComparing(TargetMatch::displayText))
                .limit(TARGET_MATCH_LIMIT).map(TargetMatch::displayText).toList();
    }

    private static Optional<TargetMatch> matchCandidate(String query, TargetCandidate candidate) {
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

        int maximumDistance = maximumEditDistance(query);
        int distance = editDistance(query, candidateText, maximumDistance);
        if (distance <= maximumDistance) {
            return Optional.of(new TargetMatch(candidate.displayText(), 2, distance, candidateText));
        }

        return Optional.empty();
    }

    private static int maximumEditDistance(String query) {
        if (query.length() <= 3) {
            return 1;
        }
        if (query.length() <= 8) {
            return 2;
        }
        return 3;
    }

    private static int editDistance(String first, String second, int cutoff) {
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

    record ResolvedSubject(CommandSubject subject, boolean recordMetadata) {
    }

    private record TargetCandidate(String matchText, String displayText) {
    }

    private record TargetMatch(String displayText, int category, int score, String sortText) {
    }
}
