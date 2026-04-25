package me.clutchy.clutchperms.common.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

/**
 * Shared Brigadier argument names used by ClutchPerms command branches.
 */
public final class CommandArguments {

    public static final String TARGET = "target";

    public static final String NODE = "node";

    public static final String ASSIGNMENT = "assignment";

    public static final String NAME = "name";

    public static final String QUERY = "query";

    public static final String GROUP = "group";

    public static final String PARENT = "parent";

    public static final String BACKUP_KIND = "backupKind";

    public static final String BACKUP_FILE = "backupFile";

    public static final String CONFIG_KEY = "configKey";

    public static final String CONFIG_VALUE = "configValue";

    public static final String PAGE = "page";

    public static final String UNKNOWN = "unknown";

    /**
     * Creates a greedy string argument used to catch unknown subcommands.
     *
     * @param <S> platform command source type
     * @return unknown argument builder
     */
    public static <S> RequiredArgumentBuilder<S, String> unknown() {
        return RequiredArgumentBuilder.argument(UNKNOWN, StringArgumentType.greedyString());
    }

    private CommandArguments() {
    }
}
