package me.clutchy.clutchperms.common.permission;

/**
 * Point-in-time resolver cache occupancy details.
 *
 * @param subjects number of subjects with cached resolver data
 * @param nodeResults number of cached subject/node resolution results
 * @param effectiveSnapshots number of cached effective-permission snapshots
 */
public record PermissionResolverCacheStats(int subjects, int nodeResults, int effectiveSnapshots) {

    /**
     * Creates immutable resolver cache statistics.
     */
    public PermissionResolverCacheStats {
        if (subjects < 0) {
            throw new IllegalArgumentException("subjects cannot be negative");
        }
        if (nodeResults < 0) {
            throw new IllegalArgumentException("nodeResults cannot be negative");
        }
        if (effectiveSnapshots < 0) {
            throw new IllegalArgumentException("effectiveSnapshots cannot be negative");
        }
    }
}
