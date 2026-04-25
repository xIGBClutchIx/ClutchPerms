package me.clutchy.clutchperms.common.display;

/**
 * Minecraft text decorations supported by ClutchPerms display text.
 */
public enum DisplayDecoration {
    OBFUSCATED('k'), BOLD('l'), STRIKETHROUGH('m'), UNDERLINED('n'), ITALIC('o');

    private final char code;

    DisplayDecoration(char code) {
        this.code = code;
    }

    /**
     * Returns the ampersand formatting code for this decoration.
     *
     * @return formatting code
     */
    public char code() {
        return code;
    }

    static DisplayDecoration fromCode(char code) {
        char normalizedCode = Character.toLowerCase(code);
        for (DisplayDecoration decoration : values()) {
            if (decoration.code == normalizedCode) {
                return decoration;
            }
        }
        return null;
    }
}
