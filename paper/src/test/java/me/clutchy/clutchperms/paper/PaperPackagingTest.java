package me.clutchy.clutchperms.paper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies Paper dependency packaging stays aligned with server-provided SQLite.
 */
class PaperPackagingTest {

    @Test
    void paperJarUsesPluginLibrariesAndDoesNotBundleSqliteOrHikari() throws IOException {
        try (JarFile jar = new JarFile(Path.of(System.getProperty("clutchperms.jar")).toFile())) {
            String pluginYaml = readEntry(jar, "plugin.yml");

            assertTrue(pluginYaml.contains("com.zaxxer:HikariCP:7.0.2"));
            assertFalse(pluginYaml.contains("sqlite-jdbc"));
            assertNull(jar.getEntry("org/sqlite/JDBC.class"));
            assertNull(jar.getEntry("com/zaxxer/hikari/HikariDataSource.class"));
        }
    }

    private static String readEntry(JarFile jar, String entryName) throws IOException {
        var entry = jar.getEntry(entryName);
        assertNotNull(entry);
        return new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
    }
}
