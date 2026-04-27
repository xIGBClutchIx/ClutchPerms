package me.clutchy.clutchperms.common.storage;

import java.net.URL;
import java.security.CodeSource;
import java.util.Objects;

/**
 * Verifies that required SQLite runtime libraries are visible before storage starts.
 */
public final class SqliteDependencyGuard {

    private static final String SQLITE_DRIVER_CLASS = "org.sqlite.JDBC";

    private static final String HIKARI_CLASS = "com.zaxxer.hikari.HikariDataSource";

    /**
     * Requires the SQLite driver and HikariCP runtime classes to be visible.
     *
     * @param mode expected dependency provisioning mode
     */
    public static void require(SqliteDependencyMode mode) {
        SqliteDependencyMode requiredMode = Objects.requireNonNull(mode, "mode");
        Class<?> hikariClass = load(HIKARI_CLASS, "HikariCP is required for ClutchPerms SQLite storage");
        Class<?> sqliteClass = load(SQLITE_DRIVER_CLASS, "SQLite JDBC is required for ClutchPerms SQLite storage");
        if (requiredMode == SqliteDependencyMode.BUNDLED_WITH_CLUTCHPERMS) {
            requireBundledWithClutchPerms(hikariClass, "HikariCP");
            requireBundledWithClutchPerms(sqliteClass, "SQLite JDBC");
        }
    }

    private static Class<?> load(String className, String message) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new PermissionStorageException(message + " but " + className + " is not visible", exception);
        }
    }

    private static void requireBundledWithClutchPerms(Class<?> dependencyClass, String dependencyName) {
        ClassLoader clutchPermsLoader = SqliteDependencyGuard.class.getClassLoader();
        ClassLoader dependencyLoader = dependencyClass.getClassLoader();
        if (dependencyLoader != clutchPermsLoader) {
            URL dependencyLocation = codeSource(dependencyClass);
            throw new PermissionStorageException(dependencyName + " must be bundled with ClutchPerms; resolved " + dependencyClass.getName() + " from "
                    + Objects.toString(dependencyLocation, "unknown location") + " using a different class loader");
        }
    }

    private static URL codeSource(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        return codeSource == null ? null : codeSource.getLocation();
    }

    private SqliteDependencyGuard() {
    }
}
