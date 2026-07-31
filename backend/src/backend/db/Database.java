package backend.db;

import backend.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Central connection factory. DAOs receive this dependency instead of reading
 * global database fields.
 */
public final class Database {
    private final AppConfig config;

    public Database(AppConfig config) {
        this.config = config;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
            config.getDbUrl(),
            config.getDbUser(),
            config.getDbPassword()
        );
    }
}
