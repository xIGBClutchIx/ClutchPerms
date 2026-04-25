package me.clutchy.clutchperms.common.display;

/**
 * Minecraft chat colors supported by ClutchPerms display text.
 */
public enum DisplayColor {
    BLACK('0'), DARK_BLUE('1'), DARK_GREEN('2'), DARK_AQUA('3'), DARK_RED('4'), DARK_PURPLE('5'), GOLD('6'), GRAY('7'), DARK_GRAY('8'), BLUE('9'), GREEN('a'), AQUA('b'), RED(
            'c'), LIGHT_PURPLE('d'), YELLOW('e'), WHITE('f');

    private final char code;

    DisplayColor(char code) {
        this.code = code;
    }

    /**
     * Returns the ampersand formatting code for this color.
     *
     * @return formatting code
     */
    public char code() {
        return code;
    }

    static DisplayColor fromCode(char code) {
        char normalizedCode = Character.toLowerCase(code);
        for (DisplayColor color : values()) {
            if (color.code == normalizedCode) {
                return color;
            }
        }
        return null;
    }
}
