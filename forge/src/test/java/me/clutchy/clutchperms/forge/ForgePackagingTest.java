package me.clutchy.clutchperms.forge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Forge jar carries the SQLite runtime dependencies ClutchPerms owns.
 */
class ForgePackagingTest {

    @Test
    void forgeJarBundlesHikariAndCanonicalSqliteJdbc() throws IOException {
        try (JarFile jar = new JarFile(Path.of(System.getProperty("clutchperms.jar")).toFile())) {
            assertNotNull(jar.getEntry("com/zaxxer/hikari/HikariDataSource.class"));
            assertNotNull(jar.getEntry("org/sqlite/JDBC.class"));
            assertTrue(readEntry(jar, "META-INF/services/java.sql.Driver").lines().anyMatch("org.sqlite.JDBC"::equals));
        }
    }

    private static String readEntry(JarFile jar, String entryName) throws IOException {
        var entry = jar.getEntry(entryName);
        assertNotNull(entry);
        return new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
    }
}
