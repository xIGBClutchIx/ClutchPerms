package me.clutchy.clutchperms.common.display;

/**
 * Display values resolved by ClutchPerms.
 */
public enum DisplaySlot {
    PREFIX("prefix"), SUFFIX("suffix");

    private final String label;

    DisplaySlot(String label) {
        this.label = label;
    }

    /**
     * Returns the user-facing slot label.
     *
     * @return slot label
     */
    public String label() {
        return label;
    }
}
