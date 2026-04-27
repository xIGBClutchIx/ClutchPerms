package me.clutchy.clutchperms.common.audit;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import me.clutchy.clutchperms.common.config.ClutchPermsAuditRetentionConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;

/**
 * Applies configured audit history retention.
 */
public final class AuditLogRetention {

    /**
     * Applies configured retention to an audit log.
     *
     * @param config active runtime config
     * @param auditLogService audit log service
     * @param clock clock used for age cutoff calculation
     * @return deleted row count
     */
    public static int apply(ClutchPermsConfig config, AuditLogService auditLogService, Clock clock) {
        ClutchPermsAuditRetentionConfig retention = Objects.requireNonNull(config, "config").audit();
        if (!retention.enabled()) {
            return 0;
        }
        AuditLogService service = Objects.requireNonNull(auditLogService, "auditLogService");
        int deleted = service.pruneOlderThan(Objects.requireNonNull(clock, "clock").instant().minus(retention.maxAgeDays(), ChronoUnit.DAYS));
        if (retention.maxEntries() > 0) {
            deleted += service.pruneBeyondNewest(retention.maxEntries());
        }
        return deleted;
    }

    private AuditLogRetention() {
    }
}
