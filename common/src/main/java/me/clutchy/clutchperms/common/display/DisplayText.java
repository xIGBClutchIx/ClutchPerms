package me.clutchy.clutchperms.common.display;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Parsed user-facing prefix or suffix text stored with ampersand Minecraft formatting codes.
 *
 * @param rawText raw stored text
 * @param segments parsed styled text spans
 */
public record DisplayText(String rawText, List<DisplaySegment> segments) {

    /**
     * Maximum raw display text length.
     */
    public static final int MAX_RAW_LENGTH = 128;

    /**
     * Creates a validated display text value.
     */
    public DisplayText {
        rawText = Objects.requireNonNull(rawText, "rawText");
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (rawText.length() > MAX_RAW_LENGTH) {
            throw new IllegalArgumentException("display text must be at most " + MAX_RAW_LENGTH + " characters");
        }
        if (plainText(segments).trim().isEmpty()) {
            throw new IllegalArgumentException("display text must not be blank");
        }
    }

    /**
     * Parses a raw ampersand-formatted display text value.
     *
     * @param rawText raw text
     * @return parsed display text
     */
    public static DisplayText parse(String rawText) {
        String requiredRawText = Objects.requireNonNull(rawText, "rawText");
        if (requiredRawText.length() > MAX_RAW_LENGTH) {
            throw new IllegalArgumentException("display text must be at most " + MAX_RAW_LENGTH + " characters");
        }
        if (requiredRawText.indexOf('\u00a7') >= 0) {
            throw new IllegalArgumentException("display text must use ampersand formatting codes, not section signs");
        }

        List<DisplaySegment> parsedSegments = new ArrayList<>();
        StringBuilder pendingText = new StringBuilder();
        DisplayColor activeColor = null;
        EnumSet<DisplayDecoration> activeDecorations = EnumSet.noneOf(DisplayDecoration.class);
        for (int index = 0; index < requiredRawText.length(); index++) {
            char character = requiredRawText.charAt(index);
            if (character != '&') {
                pendingText.append(character);
                continue;
            }

            if (index + 1 >= requiredRawText.length()) {
                throw new IllegalArgumentException("display text contains dangling formatting code &");
            }

            char code = Character.toLowerCase(requiredRawText.charAt(++index));
            if (code == '&') {
                pendingText.append('&');
                continue;
            }

            DisplayColor color = DisplayColor.fromCode(code);
            DisplayDecoration decoration = DisplayDecoration.fromCode(code);
            if (color == null && decoration == null && code != 'r') {
                throw new IllegalArgumentException(String.format(Locale.ROOT, "display text contains invalid formatting code &%s", code));
            }

            flushSegment(parsedSegments, pendingText, activeColor, activeDecorations);
            if (color != null) {
                activeColor = color;
                activeDecorations.clear();
            } else if (decoration != null) {
                activeDecorations.add(decoration);
            } else {
                activeColor = null;
                activeDecorations.clear();
            }
        }
        flushSegment(parsedSegments, pendingText, activeColor, activeDecorations);
        return new DisplayText(requiredRawText, parsedSegments);
    }

    /**
     * Returns display text without formatting codes.
     *
     * @return plain text
     */
    public String plainText() {
        return plainText(segments);
    }

    private static void flushSegment(List<DisplaySegment> segments, StringBuilder pendingText, DisplayColor color, EnumSet<DisplayDecoration> decorations) {
        if (pendingText.isEmpty()) {
            return;
        }
        segments.add(DisplaySegment.of(pendingText.toString(), color, decorations));
        pendingText.setLength(0);
    }

    private static String plainText(List<DisplaySegment> segments) {
        StringBuilder builder = new StringBuilder();
        segments.forEach(segment -> builder.append(segment.text()));
        return builder.toString();
    }
}
