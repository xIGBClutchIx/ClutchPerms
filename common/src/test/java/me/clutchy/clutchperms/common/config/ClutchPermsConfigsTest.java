package me.clutchy.clutchperms.common.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.clutchy.clutchperms.common.storage.PermissionStorageException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies shared runtime config loading and validation.
 */
class ClutchPermsConfigsTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Confirms a missing config loads as defaults without creating the file during parse.
     */
    @Test
    void missingConfigLoadsDefaults() {
        Path configFile = temporaryDirectory.resolve("config.json");

        ClutchPermsConfig config = ClutchPermsConfigs.jsonFile(configFile);

        assertEquals(ClutchPermsConfig.defaults(), config);
    }

    /**
     * Confirms default config materialization creates the expected JSON shape.
     *
     * @throws IOException when test file inspection fails
     */
    @Test
    void materializeDefaultConfigCreatesConfigFile() throws IOException {
        Path configFile = temporaryDirectory.resolve("config.json");

        ClutchPermsConfigs.materializeDefault(configFile);

        assertTrue(Files.exists(configFile));
        assertEquals(ClutchPermsConfig.defaults(), ClutchPermsConfigs.jsonFile(configFile));
        String content = Files.readString(configFile);
        assertTrue(content.contains("\"version\": 1"));
        assertTrue(content.contains("\"retentionLimit\": 10"));
        assertTrue(content.contains("\"helpPageSize\": 7"));
        assertTrue(content.contains("\"resultPageSize\": 8"));
    }

    /**
     * Confirms explicit config writes replace the file with deterministic valid JSON.
     */
    @Test
    void writeConfigReplacesConfigFile() {
        Path configFile = temporaryDirectory.resolve("config.json");
        ClutchPermsConfig config = new ClutchPermsConfig(new ClutchPermsBackupConfig(3), new ClutchPermsCommandConfig(4, 5));

        ClutchPermsConfigs.write(configFile, config);

        assertEquals(config, ClutchPermsConfigs.jsonFile(configFile));
    }

    /**
     * Confirms custom valid config values load exactly.
     *
     * @throws IOException when test setup fails
     */
    @Test
    void customConfigLoadsValues() throws IOException {
        Path configFile = temporaryDirectory.resolve("config.json");
        Files.writeString(configFile, """
                {
                  "version": 1,
                  "backups": {
                    "retentionLimit": 3
                  },
                  "commands": {
                    "helpPageSize": 4,
                    "resultPageSize": 5
                  }
                }
                """);

        ClutchPermsConfig config = ClutchPermsConfigs.jsonFile(configFile);

        assertEquals(3, config.backups().retentionLimit());
        assertEquals(4, config.commands().helpPageSize());
        assertEquals(5, config.commands().resultPageSize());
    }

    /**
     * Confirms invalid config files fail strict loading.
     *
     * @throws IOException when test setup fails
     */
    @Test
    void invalidConfigFilesFailLoad() throws IOException {
        List<String> invalidConfigs = List.of("{not-json", """
                {
                  "version": 2,
                  "backups": {
                    "retentionLimit": 10
                  },
                  "commands": {
                    "helpPageSize": 7,
                    "resultPageSize": 8
                  }
                }
                """, """
                {
                  "version": 1,
                  "unknown": true,
                  "backups": {
                    "retentionLimit": 10
                  },
                  "commands": {
                    "helpPageSize": 7,
                    "resultPageSize": 8
                  }
                }
                """, """
                {
                  "version": 1,
                  "backups": {
                    "retentionLimit": 10,
                    "unknown": true
                  },
                  "commands": {
                    "helpPageSize": 7,
                    "resultPageSize": 8
                  }
                }
                """, """
                {
                  "version": 1,
                  "backups": {
                    "retentionLimit": 10
                  },
                  "commands": {
                    "helpPageSize": 7,
                    "resultPageSize": 8,
                    "unknown": true
                  }
                }
                """, """
                {
                  "version": 1,
                  "commands": {
                    "helpPageSize": 7,
                    "resultPageSize": 8
                  }
                }
                """, """
                {
                  "version": 1,
                  "backups": {
                    "retentionLimit": "10"
                  },
                  "commands": {
                    "helpPageSize": 7,
                    "resultPageSize": 8
                  }
                }
                """, """
                {
                  "version": 1,
                  "backups": {
                    "retentionLimit": 0
                  },
                  "commands": {
                    "helpPageSize": 7,
                    "resultPageSize": 8
                  }
                }
                """, """
                {
                  "version": 1,
                  "backups": {
                    "retentionLimit": 10
                  },
                  "commands": {
                    "helpPageSize": 51,
                    "resultPageSize": 8
                  }
                }
                """, """
                {
                  "version": 1,
                  "backups": {
                    "retentionLimit": 10
                  },
                  "commands": {
                    "helpPageSize": 7,
                    "resultPageSize": 0
                  }
                }
                """);

        for (int index = 0; index < invalidConfigs.size(); index++) {
            Path configFile = temporaryDirectory.resolve("invalid-" + index + ".json");
            Files.writeString(configFile, invalidConfigs.get(index));

            assertThrows(PermissionStorageException.class, () -> ClutchPermsConfigs.jsonFile(configFile), "Config index " + index + " should fail");
        }
    }
}
