package me.clutchy.clutchperms.paper;

import java.util.UUID;

import me.clutchy.clutchperms.common.display.DisplayColor;
import me.clutchy.clutchperms.common.display.DisplayDecoration;
import me.clutchy.clutchperms.common.display.DisplayResolution;
import me.clutchy.clutchperms.common.display.DisplayResolver;
import me.clutchy.clutchperms.common.display.DisplaySegment;
import me.clutchy.clutchperms.common.display.DisplayText;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Converts shared display text into Paper Adventure components.
 */
final class PaperDisplayComponents {

    static Component chatLine(UUID subjectId, Component displayName, Component message, DisplayResolver displayResolver) {
        Component line = Component.empty();
        DisplayResolution prefix = displayResolver.resolvePrefix(subjectId);
        if (prefix.value().isPresent()) {
            line = line.append(toComponent(prefix.value().get())).append(Component.space());
        }

        line = line.append(displayName);

        DisplayResolution suffix = displayResolver.resolveSuffix(subjectId);
        if (suffix.value().isPresent()) {
            line = line.append(Component.space()).append(toComponent(suffix.value().get()));
        }

        return line.append(Component.text(": ", NamedTextColor.GRAY)).append(message);
    }

    static Component toComponent(DisplayText displayText) {
        Component component = Component.empty();
        for (DisplaySegment segment : displayText.segments()) {
            Component part = Component.text(segment.text());
            if (segment.color() != null) {
                part = part.color(color(segment.color()));
            }
            for (DisplayDecoration decoration : segment.decorations()) {
                part = part.decorate(decoration(decoration));
            }
            component = component.append(part);
        }
        return component;
    }

    private static NamedTextColor color(DisplayColor color) {
        return switch (color) {
            case BLACK -> NamedTextColor.BLACK;
            case DARK_BLUE -> NamedTextColor.DARK_BLUE;
            case DARK_GREEN -> NamedTextColor.DARK_GREEN;
            case DARK_AQUA -> NamedTextColor.DARK_AQUA;
            case DARK_RED -> NamedTextColor.DARK_RED;
            case DARK_PURPLE -> NamedTextColor.DARK_PURPLE;
            case GOLD -> NamedTextColor.GOLD;
            case GRAY -> NamedTextColor.GRAY;
            case DARK_GRAY -> NamedTextColor.DARK_GRAY;
            case BLUE -> NamedTextColor.BLUE;
            case GREEN -> NamedTextColor.GREEN;
            case AQUA -> NamedTextColor.AQUA;
            case RED -> NamedTextColor.RED;
            case LIGHT_PURPLE -> NamedTextColor.LIGHT_PURPLE;
            case YELLOW -> NamedTextColor.YELLOW;
            case WHITE -> NamedTextColor.WHITE;
        };
    }

    private static TextDecoration decoration(DisplayDecoration decoration) {
        return switch (decoration) {
            case OBFUSCATED -> TextDecoration.OBFUSCATED;
            case BOLD -> TextDecoration.BOLD;
            case STRIKETHROUGH -> TextDecoration.STRIKETHROUGH;
            case UNDERLINED -> TextDecoration.UNDERLINED;
            case ITALIC -> TextDecoration.ITALIC;
        };
    }

    private PaperDisplayComponents() {
    }
}
