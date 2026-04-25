package me.clutchy.clutchperms.common.display;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * One styled span inside a parsed display text value.
 *
 * @param text literal text
 * @param color active color, or {@code null} for platform default
 * @param decorations active decorations
 */
public record DisplaySegment(String text, DisplayColor color, Set<DisplayDecoration> decorations) {

    /**
     * Creates a validated display segment.
     */
    public DisplaySegment {
        text = Objects.requireNonNull(text, "text");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("display segment text must not be empty");
        }
        decorations = Set.copyOf(Objects.requireNonNull(decorations, "decorations"));
    }

    static DisplaySegment of(String text, DisplayColor color, EnumSet<DisplayDecoration> decorations) {
        return new DisplaySegment(text, color, EnumSet.copyOf(decorations));
    }
}
