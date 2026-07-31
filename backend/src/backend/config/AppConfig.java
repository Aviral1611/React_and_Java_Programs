package backend.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Immutable application configuration loaded from config.properties.
 */
public final class AppConfig {
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final String jwtSecret;
    private final long jwtExpirationMillis;
    private final int serverPort;
    private final Path uploadDirectory;

    private AppConfig(
            String dbUrl,
            String dbUser,
            String dbPassword,
            String jwtSecret,
            long jwtExpirationMillis,
            int serverPort,
            Path uploadDirectory) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.jwtSecret = jwtSecret;
        this.jwtExpirationMillis = jwtExpirationMillis;
        this.serverPort = serverPort;
        this.uploadDirectory = uploadDirectory;
    }

    public static AppConfig load() {
        Properties properties = new Properties();
        try (InputStream input = AppConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new IllegalStateException(
                    "config.properties was not found on the classpath. Keep it in the Eclipse src folder."
                );
            }
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config.properties", e);
        }

        String dbUrl = require(properties, "db.url");
        String dbUser = require(properties, "db.user");
        String dbPassword = require(properties, "db.password");
        String jwtSecret = require(properties, "jwt.secret");
        long jwtExpiration = parseLong(properties, "jwt.expiration", 3_600_000L);
        int serverPort = (int) parseLong(properties, "server.port", 8080L);
        Path uploadDirectory = Paths.get(
            System.getProperty("user.dir"),
            "uploads"
        ).toAbsolutePath().normalize();

        return new AppConfig(
            dbUrl,
            dbUser,
            dbPassword,
            jwtSecret,
            jwtExpiration,
            serverPort,
            uploadDirectory
        );
    }

    private static String require(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing required configuration property: " + key);
        }
        return value.trim();
    }

    private static long parseLong(Properties properties, String key, long defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Configuration property must be numeric: " + key, e);
        }
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public long getJwtExpirationMillis() {
        return jwtExpirationMillis;
    }

    public int getServerPort() {
        return serverPort;
    }

    public Path getUploadDirectory() {
        return uploadDirectory;
    }
}
