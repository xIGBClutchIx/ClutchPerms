package me.clutchy.clutchperms.common;

import java.nio.file.Path;

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
        return new JsonFileSubjectMetadataService(subjectsFile);
    }

    private SubjectMetadataServices() {
    }
}
