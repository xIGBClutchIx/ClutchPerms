package me.clutchy.clutchperms.common.command;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves Minecraft server profile-cache lookups without binding shared code to one loader mapping set.
 */
public final class ProfileCacheSubjectLookup {

    private static final List<String> CACHE_ACCESSORS = List.of("getProfileCache", "getUserCache");

    private static final List<String> ASYNC_LOOKUP_METHODS = List.of("getAsync");

    private static final List<String> LOOKUP_METHODS = List.of("get", "getOfflinePlayerProfile");

    private ProfileCacheSubjectLookup() {
    }

    /**
     * Resolves one exact player name through a modded server profile cache.
     *
     * @param server active Minecraft server instance
     * @param target exact player name
     * @return lookup future
     */
    public static CompletableFuture<Optional<CommandSubject>> resolveSubjectAsync(Object server, String target) {
        Object profileCache = findProfileCache(server);
        if (profileCache == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        CompletableFuture<?> asyncLookup = invokeStringFuture(profileCache, target, ASYNC_LOOKUP_METHODS);
        if (asyncLookup != null) {
            return asyncLookup.thenApply(ProfileCacheSubjectLookup::toCommandSubject);
        }
        return CompletableFuture.supplyAsync(() -> resolveSubject(profileCache, target));
    }

    private static Object findProfileCache(Object server) {
        return invokeNoArg(Objects.requireNonNull(server, "server"), CACHE_ACCESSORS).orElse(null);
    }

    private static Optional<CommandSubject> resolveSubject(Object profileCache, String target) {
        return toCommandSubject(invokeString(profileCache, target, LOOKUP_METHODS).orElse(null));
    }

    private static Optional<CommandSubject> toCommandSubject(Object lookupResult) {
        if (lookupResult == null) {
            return Optional.empty();
        }
        if (lookupResult instanceof Optional<?> optional) {
            return optional.isEmpty() ? Optional.empty() : toCommandSubject(optional.orElse(null));
        }
        UUID subjectId = extractUuid(lookupResult);
        String subjectName = extractName(lookupResult);
        if (subjectId == null || subjectName == null || subjectName.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new CommandSubject(subjectId, subjectName));
    }

    private static UUID extractUuid(Object profile) {
        Object value = invokeNoArg(profile, List.of("getId")).orElse(null);
        return value instanceof UUID uuid ? uuid : null;
    }

    private static String extractName(Object profile) {
        Object value = invokeNoArg(profile, List.of("getName")).orElse(null);
        return value instanceof String name ? name : null;
    }

    private static CompletableFuture<?> invokeStringFuture(Object target, String argument, List<String> methodNames) {
        Object value = invokeString(target, argument, methodNames).orElse(null);
        return value instanceof CompletableFuture<?> future ? future : null;
    }

    private static Optional<Object> invokeNoArg(Object target, List<String> methodNames) {
        for (String methodName : methodNames) {
            Method method = findMethod(target.getClass(), methodName);
            if (method == null) {
                continue;
            }
            return Optional.ofNullable(invoke(target, method, methodName));
        }
        return Optional.empty();
    }

    private static Optional<Object> invokeString(Object target, String argument, List<String> methodNames) {
        for (String methodName : methodNames) {
            Method method = findMethod(target.getClass(), methodName, String.class);
            if (method == null) {
                continue;
            }
            return Optional.ofNullable(invoke(target, method, methodName, argument));
        }
        return Optional.empty();
    }

    private static Method findMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        try {
            return type.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static Object invoke(Object target, Method method, String methodName, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access " + target.getClass().getName() + "#" + methodName, exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Invocation failed for " + target.getClass().getName() + "#" + methodName, cause);
        }
    }
}
