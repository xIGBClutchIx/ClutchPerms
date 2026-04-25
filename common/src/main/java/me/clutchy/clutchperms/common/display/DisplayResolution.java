package me.clutchy.clutchperms.common.display;

import java.util.Objects;
import java.util.Optional;

/**
 * Effective display resolution for one subject and one display slot.
 *
 * @param slot resolved display slot
 * @param value resolved value
 * @param source source tier
 * @param groupName group source when the value came from a group
 * @param depth inheritance depth for group/default sources, or {@code -1} for direct/unset
 */
public record DisplayResolution(DisplaySlot slot, Optional<DisplayText> value, Source source, String groupName, int depth) {

    /**
     * Creates a validated display resolution.
     */
    public DisplayResolution {
        slot = Objects.requireNonNull(slot, "slot");
        value = Objects.requireNonNull(value, "value");
        source = Objects.requireNonNull(source, "source");
    }

    /**
     * Display value source tier.
     */
    public enum Source {
        DIRECT, GROUP, DEFAULT, UNSET
    }
}
