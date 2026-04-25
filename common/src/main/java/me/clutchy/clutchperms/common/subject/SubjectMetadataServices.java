package me.clutchy.clutchperms.common.subject;

import java.nio.file.Path;

import me.clutchy.clutchperms.common.storage.StorageWriteOptions;

/**
 * Factory methods for subject metadata services.
 */
public final class SubjectMetadataServices {

    /**
     * Creates a JSON-backed subject metadata service.
     *
     * @param subjectsFile path to the JSON metadata file
     * @return subject metadata service backed by {@code subjectsFile}
     */
    public static SubjectMetadataService jsonFile(Path subjectsFile) {
        return jsonFile(subjectsFile, StorageWriteOptions.defaults());
    }

    /**
     * Creates a JSON-backed subject metadata service.
     *
     * @param subjectsFile path to the JSON metadata file
     * @param writeOptions storage write options used for future mutations
     * @return subject metadata service backed by {@code subjectsFile}
     */
    public static SubjectMetadataService jsonFile(Path subjectsFile, StorageWriteOptions writeOptions) {
        return new JsonFileSubjectMetadataService(subjectsFile, StorageWriteOptions.defaultIfNull(writeOptions));
    }

    private SubjectMetadataServices() {
    }
}
