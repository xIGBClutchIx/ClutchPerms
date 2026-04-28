package me.clutchy.clutchperms.paper;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.profile.PlayerProfile;
import org.junit.jupiter.api.Test;

import me.clutchy.clutchperms.common.command.CommandSubject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperClutchPermsCommandTest {

    @Test
    void cachedSubjectUsesOfflinePlayerCache() {
        UUID subjectId = UUID.randomUUID();
        Server server = proxy(Server.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getOfflinePlayerIfCached" -> proxy(OfflinePlayer.class, (playerProxy, playerMethod, playerArguments) -> switch (playerMethod.getName()) {
                case "getUniqueId" -> subjectId;
                case "getName" -> "CachedUser";
                default -> defaultValue(playerMethod.getReturnType());
            });
            default -> defaultValue(method.getReturnType());
        });

        assertEquals(Optional.of(new CommandSubject(subjectId, "CachedUser")), PaperClutchPermsCommand.cachedSubject(server, "CachedUser"));
    }

    @Test
    void cachedSubjectFallsBackToTargetWhenCachedNameIsMissing() {
        UUID subjectId = UUID.randomUUID();
        Server server = proxy(Server.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getOfflinePlayerIfCached" -> proxy(OfflinePlayer.class, (playerProxy, playerMethod, playerArguments) -> switch (playerMethod.getName()) {
                case "getUniqueId" -> subjectId;
                case "getName" -> null;
                default -> defaultValue(playerMethod.getReturnType());
            });
            default -> defaultValue(method.getReturnType());
        });

        assertEquals(Optional.of(new CommandSubject(subjectId, "FallbackUser")), PaperClutchPermsCommand.cachedSubject(server, "FallbackUser"));
    }

    @Test
    void asyncResolutionUsesUpdatedProfile() {
        UUID subjectId = UUID.randomUUID();
        PlayerProfile updatedProfile = proxy(PlayerProfile.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getUniqueId" -> subjectId;
            case "getName" -> "ResolvedUser";
            default -> defaultValue(method.getReturnType());
        });
        PlayerProfile initialProfile = proxy(PlayerProfile.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "update" -> CompletableFuture.completedFuture(updatedProfile);
            default -> defaultValue(method.getReturnType());
        });
        Server server = proxy(Server.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "createPlayerProfile" -> initialProfile;
            default -> defaultValue(method.getReturnType());
        });

        assertEquals(Optional.of(new CommandSubject(subjectId, "ResolvedUser")), PaperClutchPermsCommand.resolveSubjectAsync(server, "ResolvedUser").join());
    }

    @Test
    void asyncResolutionTreatsUnsupportedLookupsAsUnavailable() {
        PlayerProfile unsupportedProfile = proxy(PlayerProfile.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "update" -> throw new UnsupportedOperationException("not implemented");
            default -> defaultValue(method.getReturnType());
        });
        Server server = proxy(Server.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "createPlayerProfile" -> unsupportedProfile;
            default -> defaultValue(method.getReturnType());
        });

        assertTrue(PaperClutchPermsCommand.resolveSubjectAsync(server, "MissingUser").join().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
