package me.clutchy.clutchperms.common.runtime;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Platform runtime refresh callbacks invoked after successful storage mutations.
 */
public record ClutchPermsRuntimeHooks(Consumer<UUID> subjectRefresher, Runnable fullRefresher) {

    /**
     * Creates no-op runtime hooks.
     *
     * @return no-op hooks
     */
    public static ClutchPermsRuntimeHooks noop() {
        return new ClutchPermsRuntimeHooks(subjectId -> {
        }, () -> {
        });
    }

    /**
     * Validates hook callbacks.
     */
    public ClutchPermsRuntimeHooks {
        subjectRefresher = Objects.requireNonNull(subjectRefresher, "subjectRefresher");
        fullRefresher = Objects.requireNonNull(fullRefresher, "fullRefresher");
    }

    /**
     * Refreshes one subject in the platform runtime bridge.
     *
     * @param subjectId changed subject
     */
    public void refreshSubject(UUID subjectId) {
        subjectRefresher.accept(Objects.requireNonNull(subjectId, "subjectId"));
    }

    /**
     * Refreshes all platform runtime permission state.
     */
    public void refreshAll() {
        fullRefresher.run();
    }
}
