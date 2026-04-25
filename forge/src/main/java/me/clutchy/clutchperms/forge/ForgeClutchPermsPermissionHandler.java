package me.clutchy.clutchperms.forge;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionValue;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.permission.handler.IPermissionHandler;
import net.minecraftforge.server.permission.nodes.PermissionDynamicContext;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

/**
 * Forge permission handler that resolves registered Boolean permission nodes from effective ClutchPerms assignments.
 */
final class ForgeClutchPermsPermissionHandler implements IPermissionHandler {

    static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(ClutchPermsForgeMod.MOD_ID, "direct");

    static final List<PermissionNode<Boolean>> COMMAND_NODES = createCommandNodes();

    private final Supplier<PermissionResolver> permissionResolverSupplier;

    private final Set<PermissionNode<?>> registeredNodes;

    ForgeClutchPermsPermissionHandler(PermissionResolver permissionResolver, Collection<PermissionNode<?>> registeredNodes) {
        this(() -> permissionResolver, registeredNodes);
    }

    ForgeClutchPermsPermissionHandler(Supplier<PermissionResolver> permissionResolverSupplier, Collection<PermissionNode<?>> registeredNodes) {
        this.permissionResolverSupplier = permissionResolverSupplier;
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

        PermissionValue value = permissionResolverSupplier.get().resolve(subjectId, node.getNodeName()).value();
        return switch (value) {
            case TRUE -> (T) Boolean.TRUE;
            case FALSE -> (T) Boolean.FALSE;
            case UNSET -> defaultValue;
        };
    }

    static List<String> booleanNodeNames(Collection<PermissionNode<?>> nodes) {
        return nodes.stream().filter(node -> node.getType() == PermissionTypes.BOOLEAN).map(PermissionNode::getNodeName).sorted().toList();
    }

    static PermissionNode<?>[] commandNodes() {
        return COMMAND_NODES.toArray(PermissionNode<?>[]::new);
    }

    private static List<PermissionNode<Boolean>> createCommandNodes() {
        return PermissionNodes.commandNodes().stream().map(ForgeClutchPermsPermissionHandler::createCommandNode).toList();
    }

    private static PermissionNode<Boolean> createCommandNode(String nodeName) {
        String prefix = ClutchPermsForgeMod.MOD_ID + ".";
        if (!nodeName.startsWith(prefix)) {
            throw new IllegalStateException("Forge command node does not use the ClutchPerms namespace: " + nodeName);
        }
        PermissionNode<Boolean> node = new PermissionNode<>(ClutchPermsForgeMod.MOD_ID, nodeName.substring(prefix.length()), PermissionTypes.BOOLEAN,
                (player, subjectId, context) -> Boolean.FALSE);
        node.setInformation(Component.literal(nodeName), Component.literal("Allows running the matching ClutchPerms command."));
        if (!nodeName.equals(node.getNodeName())) {
            throw new IllegalStateException("Forge command node does not match shared command node: " + nodeName);
        }
        return node;
    }
}
