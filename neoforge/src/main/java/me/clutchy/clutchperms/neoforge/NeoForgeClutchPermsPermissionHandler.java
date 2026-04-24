package me.clutchy.clutchperms.neoforge;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import me.clutchy.clutchperms.common.PermissionNodes;
import me.clutchy.clutchperms.common.PermissionService;
import me.clutchy.clutchperms.common.PermissionValue;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.handler.IPermissionHandler;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContext;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * NeoForge permission handler that resolves registered Boolean permission nodes from persisted ClutchPerms direct assignments.
 */
final class NeoForgeClutchPermsPermissionHandler implements IPermissionHandler {

    static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(ClutchPermsNeoForgeMod.MOD_ID, "direct");

    static final PermissionNode<Boolean> ADMIN_NODE = createAdminNode();

    private final PermissionService permissionService;

    private final Set<PermissionNode<?>> registeredNodes;

    NeoForgeClutchPermsPermissionHandler(PermissionService permissionService, Collection<PermissionNode<?>> registeredNodes) {
        this.permissionService = permissionService;
        this.registeredNodes = Collections.unmodifiableSet(new HashSet<>(registeredNodes));
    }

    @Override
    public Identifier getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public Set<PermissionNode<?>> getRegisteredNodes() {
        return registeredNodes;
    }

    @Override
    public <T> T getPermission(ServerPlayer player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
        return resolve(player.getUUID(), node, node.getDefaultResolver().resolve(player, player.getUUID(), context));
    }

    @Override
    public <T> T getOfflinePermission(UUID player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
        return resolve(player, node, node.getDefaultResolver().resolve(null, player, context));
    }

    @SuppressWarnings("unchecked")
    <T> T resolve(UUID subjectId, PermissionNode<T> node, T defaultValue) {
        if (node.getType() != PermissionTypes.BOOLEAN) {
            return defaultValue;
        }

        PermissionValue value = permissionService.getPermission(subjectId, node.getNodeName());
        return switch (value) {
            case TRUE -> (T) Boolean.TRUE;
            case FALSE -> (T) Boolean.FALSE;
            case UNSET -> defaultValue;
        };
    }

    private static PermissionNode<Boolean> createAdminNode() {
        PermissionNode<Boolean> node = new PermissionNode<>(ClutchPermsNeoForgeMod.MOD_ID, "admin", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.FALSE);
        node.setInformation(Component.literal("ClutchPerms Admin"), Component.literal("Allows managing ClutchPerms permissions."));
        if (!PermissionNodes.ADMIN.equals(node.getNodeName())) {
            throw new IllegalStateException("NeoForge admin node does not match shared admin node");
        }
        return node;
    }
}
