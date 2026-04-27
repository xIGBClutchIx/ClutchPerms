package me.clutchy.clutchperms.common.subject;

import java.util.Objects;

import me.clutchy.clutchperms.common.storage.SqliteStore;

/**
 * Factory methods for subject metadata services.
 */
public final class SubjectMetadataServices {

    public static SubjectMetadataService sqlite(SqliteStore store) {
        return new SqliteSubjectMetadataService(Objects.requireNonNull(store, "store"));
    }

    private SubjectMetadataServices() {
    }
}
