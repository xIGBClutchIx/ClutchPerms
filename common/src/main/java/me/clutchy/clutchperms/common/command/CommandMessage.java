package me.clutchy.clutchperms.common.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Platform-neutral command feedback with styled text segments and a plain-text fallback.
 */
public final class CommandMessage {

    private final List<Segment> segments;

    private CommandMessage(List<Segment> segments) {
        this.segments = List.copyOf(segments);
    }

    /**
     * Creates a message from styled segments.
     *
     * @param segments styled text segments
     * @return command message
     */
    public static CommandMessage of(Segment... segments) {
        return new CommandMessage(Arrays.asList(segments));
    }

    /**
     * Creates a single-segment message.
     *
     * @param text plain text
     * @param color segment color
     * @return command message
     */
    public static CommandMessage text(String text, Color color) {
        return of(segment(text, color));
    }

    /**
     * Creates a single-segment message with the default white color.
     *
     * @param text plain text
     * @return command message
     */
    public static CommandMessage plain(String text) {
        return text(text, Color.WHITE);
    }

    /**
     * Creates a styled text segment.
     *
     * @param text plain text
     * @param color segment color
     * @return styled segment
     */
    public static Segment segment(String text, Color color) {
        return new Segment(text, color, false);
    }

    /**
     * Creates a bold styled text segment.
     *
     * @param text plain text
     * @param color segment color
     * @return styled segment
     */
    public static Segment bold(String text, Color color) {
        return new Segment(text, color, true);
    }

    /**
     * Returns immutable styled segments in render order.
     *
     * @return styled segments
     */
    public List<Segment> segments() {
        return segments;
    }

    /**
     * Returns this message without styling.
     *
     * @return concatenated plain text
     */
    public String plainText() {
        StringBuilder builder = new StringBuilder();
        segments.forEach(segment -> builder.append(segment.text()));
        return builder.toString();
    }

    /**
     * Returns a new message with another message appended.
     *
     * @param message message to append
     * @return combined message
     */
    public CommandMessage append(CommandMessage message) {
        Objects.requireNonNull(message, "message");
        List<Segment> combined = new ArrayList<>(segments);
        combined.addAll(message.segments);
        return new CommandMessage(combined);
    }

    @Override
    public String toString() {
        return plainText();
    }

    /**
     * Shared command message colors that every platform adapter maps to native chat colors.
     */
    public enum Color {
        AQUA, GREEN, RED, YELLOW, GRAY, WHITE
    }

    /**
     * One styled text segment.
     *
     * @param text segment text
     * @param color segment color
     * @param bold whether the segment should be bold
     */
    public record Segment(String text, Color color, boolean bold) {

        public Segment {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(color, "color");
        }
    }
}
