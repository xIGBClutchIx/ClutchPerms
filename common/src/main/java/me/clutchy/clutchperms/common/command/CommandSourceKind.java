package me.clutchy.clutchperms.common.command;

/**
 * Describes the platform command source categories used by shared ClutchPerms command authorization.
 */
public enum CommandSourceKind {
    /**
     * A player command source with a stable UUID.
     */
    PLAYER,

    /**
     * A trusted console or remote-console source used to bootstrap administration.
     */
    CONSOLE,

    /**
     * Any other source, such as command blocks or automated execution contexts.
     */
    OTHER
}
