package me.clutchy.clutchperms.common.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Platform-neutral command feedback with styled text segments, optional interactions, and a plain-text fallback.
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
     * Creates click metadata that suggests a command in chat.
     *
     * @param command command text to paste
     * @return click metadata
     */
    public static Click clickSuggest(String command) {
        return new Click(ClickAction.SUGGEST_COMMAND, command);
    }

    /**
     * Creates click metadata that runs a command.
     *
     * @param command command text to run
     * @return click metadata
     */
    public static Click clickRun(String command) {
        return new Click(ClickAction.RUN_COMMAND, command);
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

    /**
     * Returns a new message with the same click metadata applied to every segment.
     *
     * @param click click metadata
     * @return interactive message
     */
    public CommandMessage withClick(Click click) {
        Objects.requireNonNull(click, "click");
        return mapSegments(segment -> segment.withClick(click));
    }

    /**
     * Returns a new message with the same hover text applied to every segment.
     *
     * @param hover hover text
     * @return interactive message
     */
    public CommandMessage withHover(CommandMessage hover) {
        Objects.requireNonNull(hover, "hover");
        return mapSegments(segment -> segment.withHover(hover));
    }

    /**
     * Returns a new message with the same click and hover metadata applied to every segment.
     *
     * @param click click metadata
     * @param hover hover text
     * @return interactive message
     */
    public CommandMessage withInteraction(Click click, CommandMessage hover) {
        Objects.requireNonNull(click, "click");
        Objects.requireNonNull(hover, "hover");
        return mapSegments(segment -> segment.withClick(click).withHover(hover));
    }

    private CommandMessage mapSegments(SegmentMapper mapper) {
        List<Segment> mapped = new ArrayList<>(segments.size());
        segments.forEach(segment -> mapped.add(mapper.map(segment)));
        return new CommandMessage(mapped);
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
     * Supported platform-neutral click actions.
     */
    public enum ClickAction {
        SUGGEST_COMMAND, RUN_COMMAND
    }

    /**
     * Platform-neutral click metadata.
     *
     * @param action click action
     * @param value action value
     */
    public record Click(ClickAction action, String value) {

        public Click {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * One styled text segment.
     *
     * @param text segment text
     * @param color segment color
     * @param bold whether the segment should be bold
     * @param click optional click metadata
     * @param hover optional hover text
     */
    public record Segment(String text, Color color, boolean bold, Click click, CommandMessage hover) {

        public Segment(String text, Color color, boolean bold) {
            this(text, color, bold, null, null);
        }

        public Segment {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(color, "color");
        }

        private Segment withClick(Click click) {
            return new Segment(text, color, bold, click, hover);
        }

        private Segment withHover(CommandMessage hover) {
            return new Segment(text, color, bold, click, hover);
        }
    }

    private interface SegmentMapper {

        Segment map(Segment segment);
    }
}
