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
        assertTrue(content.contains("\"schedule\""));
        assertTrue(content.contains("\"enabled\": false"));
        assertTrue(content.contains("\"intervalMinutes\": 60"));
        assertTrue(content.contains("\"runOnStartup\": false"));
        assertTrue(content.contains("\"audit\""));
        assertTrue(content.contains("\"retention\""));
        assertTrue(content.contains("\"maxAgeDays\": 90"));
        assertTrue(content.contains("\"maxEntries\": 0"));
        assertTrue(content.contains("\"helpPageSize\": 7"));
        assertTrue(content.contains("\"resultPageSize\": 8"));
        assertTrue(content.contains("\"chat\""));
        assertTrue(content.contains("\"enabled\": true"));
        assertTrue(content.contains("\"paper\""));
        assertTrue(content.contains("\"replaceOpCommands\": true"));
    }

    /**
     * Confirms explicit config writes replace the file with deterministic valid JSON.
     */
    @Test
    void writeConfigReplacesConfigFile() {
        Path configFile = temporaryDirectory.resolve("config.json");
        ClutchPermsConfig config = new ClutchPermsConfig(new ClutchPermsBackupConfig(3), new ClutchPermsAuditRetentionConfig(false, 30, 100), new ClutchPermsCommandConfig(4, 5),
                new ClutchPermsChatConfig(false), new ClutchPermsPaperConfig(false));

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
                    "retentionLimit": 3,
                    "schedule": {
                      "enabled": true,
                      "intervalMinutes": 120,
                      "runOnStartup": true
                    }
                  },
                  "audit": {
                    "retention": {
                      "enabled": false,
                      "maxAgeDays": 30,
                      "maxEntries": 100
                    }
                  },
                  "commands": {
                    "helpPageSize": 4,
                    "resultPageSize": 5
                  },
                  "chat": {
                    "enabled": false
                  },
                  "paper": {
                    "replaceOpCommands": false
                  }
                }
                """);

        ClutchPermsConfig config = ClutchPermsConfigs.jsonFile(configFile);

        assertEquals(3, config.backups().retentionLimit());
        assertEquals(true, config.backups().schedule().enabled());
        assertEquals(120, config.backups().schedule().intervalMinutes());
        assertEquals(true, config.backups().schedule().runOnStartup());
        assertEquals(false, config.audit().enabled());
        assertEquals(30, config.audit().maxAgeDays());
        assertEquals(100, config.audit().maxEntries());
        assertEquals(4, config.commands().helpPageSize());
        assertEquals(5, config.commands().resultPageSize());
        assertEquals(false, config.chat().enabled());
        assertEquals(false, config.paper().replaceOpCommands());
    }

    /**
     * Confirms configs from before chat settings load with chat formatting enabled.
     *
     * @throws IOException when test setup fails
     */
    @Test
    void missingChatConfigLoadsDefaultEnabled() throws IOException {
        Path configFile = temporaryDirectory.resolve("legacy-config.json");
        Files.writeString(configFile, """
                {
                  "version": 1,
                  "backups": {
                    "retentionLimit": 10
                  },
                  "commands": {
                    "helpPageSize": 7,
                    "resultPageSize": 8
                  }
                }
                """);

        ClutchPermsConfig config = ClutchPermsConfigs.jsonFile(configFile);

        assertEquals(true, config.chat().enabled());
        assertEquals(true, config.paper().replaceOpCommands());
        assertEquals(ClutchPermsBackupScheduleConfig.defaults(), config.backups().schedule());
        assertEquals(ClutchPermsAuditRetentionConfig.defaults(), config.audit());
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
                """, """
                {
                  "version": 1,
                  "backups": {
                    "retentionLimit": 10
                  },
                  "commands": {
                    "helpPageSize": 7,
                    "resultPageSize": 8
                  },
                  "chat": {
                    "enabled": true,
                    "unknown": true
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
                    "resultPageSize": 8
                  },
                  "chat": true
                }
                """, """
                {
                  "version": 1,
                  "backups": {
                    "retentionLimit": 10
                  },
                  "commands": {
                    "helpPageSize": 7,
                    "resultPageSize": 8
                  },
                  "chat": {}
                }
                """, """
                {
                  "version": 1,
                  "backups": {
                    "retentionLimit": 10
                  },
                  "commands": {
                    "helpPageSize": 7,
                    "resultPageSize": 8
                  },
                  "chat": {
                    "enabled": "true"
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
                    "resultPageSize": 8
                  },
                  "paper": true
                }
                """, """
                {
                  "version": 1,
                  "backups": {
                    "retentionLimit": 10
                  },
                  "commands": {
                    "helpPageSize": 7,
                    "resultPageSize": 8
                  },
                  "paper": {}
                }
                """, """
                {
                  "version": 1,
                  "backups": {
                    "retentionLimit": 10
                  },
                  "commands": {
                    "helpPageSize": 7,
                    "resultPageSize": 8
                  },
                  "paper": {
                    "replaceOpCommands": "false"
                  }
                }
                """, """
                {
                  "version": 1,
                  "backups": {
                    "retentionLimit": 10,
                    "schedule": true
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
                    "schedule": {
                      "enabled": false,
                      "intervalMinutes": 60,
                      "runOnStartup": false,
                      "unknown": true
                    }
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
                    "schedule": {
                      "enabled": "false",
                      "intervalMinutes": 60,
                      "runOnStartup": false
                    }
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
                    "schedule": {
                      "enabled": false,
                      "intervalMinutes": 4,
                      "runOnStartup": false
                    }
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
                    "schedule": {
                      "enabled": false,
                      "intervalMinutes": 10081,
                      "runOnStartup": false
                    }
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
                  "audit": true,
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
                  "audit": {
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
                  "audit": {
                    "retention": {
                      "enabled": true,
                      "maxAgeDays": 90,
                      "maxEntries": 0,
                      "unknown": true
                    }
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
                  "audit": {
                    "retention": {
                      "enabled": "true",
                      "maxAgeDays": 90,
                      "maxEntries": 0
                    }
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
                  "audit": {
                    "retention": {
                      "enabled": true,
                      "maxAgeDays": 0,
                      "maxEntries": 0
                    }
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
                  "audit": {
                    "retention": {
                      "enabled": true,
                      "maxAgeDays": 90,
                      "maxEntries": 1000001
                    }
                  },
                  "commands": {
                    "helpPageSize": 7,
                    "resultPageSize": 8
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
