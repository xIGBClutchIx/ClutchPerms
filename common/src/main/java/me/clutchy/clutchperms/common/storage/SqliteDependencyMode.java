package me.clutchy.clutchperms.common.storage;

/**
 * Describes how the active platform is expected to provide SQLite and HikariCP classes.
 */
public enum SqliteDependencyMode {

    /**
     * Tests and plain common-code consumers only require visible classes.
     */
    ANY_VISIBLE,

    /**
     * Paper provides SQLite itself and loads HikariCP through plugin libraries.
     */
    PAPER_BUILT_IN_SQLITE,

    /**
     * Mod-loader artifacts bundle SQLite JDBC and HikariCP inside the ClutchPerms jar.
     */
    BUNDLED_WITH_CLUTCHPERMS
}
