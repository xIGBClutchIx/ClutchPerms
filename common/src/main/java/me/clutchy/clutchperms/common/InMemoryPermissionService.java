package me.clutchy.clutchperms.common;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe in-memory {@link PermissionService} implementation used by the current scaffold.
 *
 * <p>
 * This implementation is intentionally small. It stores explicit permission values by subject UUID and normalized permission node without any persistence, inheritance, or context
 * handling.
 */
public final class InMemoryPermissionService implements PermissionService {

    /**
     * Stores explicit permissions per subject and normalized permission node.
     */
    private final ConcurrentMap<UUID, ConcurrentMap<String, PermissionValue>> permissions = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionValue getPermission(UUID subjectId, String node) {
        Objects.requireNonNull(subjectId, "subjectId");
        String normalizedNode = normalizeNode(node);

        // Missing subjects are treated the same as missing nodes: no explicit value exists yet.
        Map<String, PermissionValue> subjectPermissions = permissions.get(subjectId);
        if (subjectPermissions == null) {
            return PermissionValue.UNSET;
        }

        return subjectPermissions.getOrDefault(normalizedNode, PermissionValue.UNSET);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPermission(UUID subjectId, String node, PermissionValue value) {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(value, "value");
        String normalizedNode = normalizeNode(node);

        // Treating UNSET as a clear keeps the backing storage compact and predictable.
        if (value == PermissionValue.UNSET) {
            clearPermission(subjectId, normalizedNode);
            return;
        }

        permissions.computeIfAbsent(subjectId, ignored -> new ConcurrentHashMap<>()).put(normalizedNode, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearPermission(UUID subjectId, String node) {
        Objects.requireNonNull(subjectId, "subjectId");
        String normalizedNode = normalizeNode(node);

        // Remove empty subject maps so unused identities do not accumulate indefinitely.
        permissions.computeIfPresent(subjectId, (ignored, subjectPermissions) -> {
            subjectPermissions.remove(normalizedNode);
            return subjectPermissions.isEmpty() ? null : subjectPermissions;
        });
    }

    /**
     * Normalizes permission nodes into the storage format used by this implementation.
     *
     * @param node raw permission node supplied by the caller
     * @return a trimmed lower-case node suitable for map storage
     * @throws NullPointerException if {@code node} is {@code null}
     * @throws IllegalArgumentException if the normalized node is blank
     */
    private static String normalizeNode(String node) {
        String normalizedNode = Objects.requireNonNull(node, "node").trim().toLowerCase(Locale.ROOT);
        if (normalizedNode.isEmpty()) {
            throw new IllegalArgumentException("node must not be blank");
        }
        return normalizedNode;
    }
}
