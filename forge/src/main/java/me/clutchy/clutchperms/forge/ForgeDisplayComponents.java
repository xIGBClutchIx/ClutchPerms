package me.clutchy.clutchperms.forge;

import java.util.UUID;

import me.clutchy.clutchperms.common.display.DisplayColor;
import me.clutchy.clutchperms.common.display.DisplayDecoration;
import me.clutchy.clutchperms.common.display.DisplayResolution;
import me.clutchy.clutchperms.common.display.DisplayResolver;
import me.clutchy.clutchperms.common.display.DisplaySegment;
import me.clutchy.clutchperms.common.display.DisplayText;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Converts shared display text into vanilla Minecraft components on Forge.
 */
final class ForgeDisplayComponents {

    static Component chatLine(UUID subjectId, Component displayName, Component message, DisplayResolver displayResolver) {
        MutableComponent line = Component.empty();
        DisplayResolution prefix = displayResolver.resolvePrefix(subjectId);
        if (prefix.value().isPresent()) {
            line.append(toComponent(prefix.value().get())).append(Component.literal(" "));
        }

        line.append(displayName);

        DisplayResolution suffix = displayResolver.resolveSuffix(subjectId);
        if (suffix.value().isPresent()) {
            line.append(Component.literal(" ")).append(toComponent(suffix.value().get()));
        }

        return line.append(Component.literal(": ").withStyle(ChatFormatting.GRAY)).append(message);
    }

    static Component toComponent(DisplayText displayText) {
        MutableComponent component = Component.empty();
        for (DisplaySegment segment : displayText.segments()) {
            MutableComponent part = Component.literal(segment.text());
            if (segment.color() != null) {
                part = part.withStyle(color(segment.color()));
            }
            for (DisplayDecoration decoration : segment.decorations()) {
                part = applyDecoration(part, decoration);
            }
            component.append(part);
        }
        return component;
    }

    private static MutableComponent applyDecoration(MutableComponent component, DisplayDecoration decoration) {
        return switch (decoration) {
            case OBFUSCATED -> component.withStyle(style -> style.withObfuscated(true));
            case BOLD -> component.withStyle(style -> style.withBold(true));
            case STRIKETHROUGH -> component.withStyle(style -> style.withStrikethrough(true));
            case UNDERLINED -> component.withStyle(style -> style.withUnderlined(true));
            case ITALIC -> component.withStyle(style -> style.withItalic(true));
        };
    }

    private static ChatFormatting color(DisplayColor color) {
        return switch (color) {
            case BLACK -> ChatFormatting.BLACK;
            case DARK_BLUE -> ChatFormatting.DARK_BLUE;
            case DARK_GREEN -> ChatFormatting.DARK_GREEN;
            case DARK_AQUA -> ChatFormatting.DARK_AQUA;
            case DARK_RED -> ChatFormatting.DARK_RED;
            case DARK_PURPLE -> ChatFormatting.DARK_PURPLE;
            case GOLD -> ChatFormatting.GOLD;
            case GRAY -> ChatFormatting.GRAY;
            case DARK_GRAY -> ChatFormatting.DARK_GRAY;
            case BLUE -> ChatFormatting.BLUE;
            case GREEN -> ChatFormatting.GREEN;
            case AQUA -> ChatFormatting.AQUA;
            case RED -> ChatFormatting.RED;
            case LIGHT_PURPLE -> ChatFormatting.LIGHT_PURPLE;
            case YELLOW -> ChatFormatting.YELLOW;
            case WHITE -> ChatFormatting.WHITE;
        };
    }

    private ForgeDisplayComponents() {
    }
}
