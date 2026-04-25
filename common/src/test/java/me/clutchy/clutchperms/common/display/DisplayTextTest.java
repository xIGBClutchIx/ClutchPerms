package me.clutchy.clutchperms.common.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies ampersand-formatted display text parsing.
 */
class DisplayTextTest {

    @Test
    void parsesColorsDecorationsResetAndLiteralAmpersands() {
        DisplayText text = DisplayText.parse("&7[&aAdmin&7] && &lName&r!");

        assertEquals("&7[&aAdmin&7] && &lName&r!", text.rawText());
        assertEquals("[Admin] & Name!", text.plainText());
        assertTrue(text.segments().stream().anyMatch(segment -> segment.text().equals("[") && segment.color() == DisplayColor.GRAY));
        assertTrue(text.segments().stream().anyMatch(segment -> segment.text().equals("Admin") && segment.color() == DisplayColor.GREEN));
        assertTrue(text.segments().stream().anyMatch(segment -> segment.text().equals("Name") && segment.decorations().contains(DisplayDecoration.BOLD)));
        assertTrue(text.segments().stream().anyMatch(segment -> segment.text().equals("!") && segment.color() == null && segment.decorations().isEmpty()));
    }

    @Test
    void rejectsInvalidDisplayText() {
        assertThrows(IllegalArgumentException.class, () -> DisplayText.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> DisplayText.parse("§cBad"));
        assertThrows(IllegalArgumentException.class, () -> DisplayText.parse("&xBad"));
        assertThrows(IllegalArgumentException.class, () -> DisplayText.parse("Bad&"));
        assertThrows(IllegalArgumentException.class, () -> DisplayText.parse("x".repeat(DisplayText.MAX_RAW_LENGTH + 1)));
    }
}
