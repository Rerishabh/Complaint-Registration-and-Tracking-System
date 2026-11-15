package com.complainttracking.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DatabaseConnection - Singleton JDBC connection manager.
 *
 * <p>Credentials are read from environment variables (preferred) or
 * fall back to a local {@code db.properties} file that is excluded
 * from version control via .gitignore.</p>
 *
 * <p>Environment variables:</p>
 * <ul>
 *   <li>{@code DB_URL}  – full JDBC URL, e.g.
 *       {@code jdbc:mysql://localhost:3306/complaint_tracking_db?useSSL=false&serverTimezone=UTC}</li>
 *   <li>{@code DB_USER} – MySQL username</li>
 *   <li>{@code DB_PASS} – MySQL password</li>
 * </ul>
 */
public final class DatabaseConnection {

    private static final Logger LOGGER =
            Logger.getLogger(DatabaseConnection.class.getName());

    // ---------------------------------------------------------------
    // Default connection parameters – overridden by env vars.
    // These defaults assume a standard local MySQL 8 installation.
    // NEVER commit real credentials here.
    // ---------------------------------------------------------------
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/complaint_tracking_db"
            + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASS = "";   // empty – set via env var

    /** Singleton connection (not thread-safe; sufficient for desktop use). */
    private static Connection connection = null;

    // Prevent instantiation
    private DatabaseConnection() { }

    /**
     * Returns a live {@link Connection}, (re)creating it when necessary.
     *
     * @return a valid {@link Connection} to the database
     * @throws SQLException if the connection cannot be established
     */
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = createConnection();
        }
        return connection;
    }

    /**
     * Creates a new JDBC connection using environment variables or
     * external db.properties when available, falling back to compiled-in defaults.
     */
    private static Connection createConnection() throws SQLException {
        String url  = resolveConfig("DB_URL",  "db.url",  DEFAULT_URL);
        String user = resolveConfig("DB_USER", "db.user", DEFAULT_USER);
        String pass = resolveConfig("DB_PASS", "db.pass", DEFAULT_PASS);

        LOGGER.log(Level.INFO,
                "Connecting to database: {0} as user: {1}", new Object[]{url, user});

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found on classpath.", e);
        }

        Connection conn = DriverManager.getConnection(url, user, pass);
        LOGGER.log(Level.INFO, "Database connection established successfully.");
        return conn;
    }

    /**
     * Closes the current connection if open.
     * Call this during application shutdown.
     */
    public static void closeConnection() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    LOGGER.log(Level.INFO, "Database connection closed.");
                }
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error while closing connection.", e);
            } finally {
                connection = null;
            }
        }
    }

    /**
     * Tests whether the database is reachable.
     *
     * @return {@code true} if a connection can be obtained
     */
    public static boolean testConnection() {
        try {
            Connection conn = getConnection();
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database connection test failed.", e);
            return false;
        }
    }

    // ---------------------------------------------------------------
    // Helpers & External Configuration
    // ---------------------------------------------------------------

    /**
     * Resolves a configuration value with the following precedence:
     * <ol>
     *   <li>Environment variable (e.g. {@code DB_PASS})</li>
     *   <li>Java System property (e.g. {@code DB_PASS} or {@code db.pass})</li>
     *   <li>External {@code db.properties} file in working directory or {@code ~/.crts/db.properties}</li>
     *   <li>Fallback default</li>
     * </ol>
     */
    private static String resolveConfig(String envKey, String propKey, String fallback) {
        // 1. Environment variable
        String value = System.getenv(envKey);
        if (value != null && !value.isBlank()) {
            return value;
        }

        // 2. Java system property
        value = System.getProperty(envKey);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = System.getProperty(propKey);
        if (value != null && !value.isBlank()) {
            return value;
        }

        // 3. External db.properties file
        Properties props = loadExternalProperties();
        value = props.getProperty(propKey);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = props.getProperty(envKey);
        if (value != null && !value.isBlank()) {
            return value;
        }

        // 4. Default fallback
        return fallback;
    }

    /**
     * Loads configuration from an external db.properties file if present.
     * Looks in:
     * 1. Current working directory (./db.properties)
     * 2. User home directory (~/.crts/db.properties)
     */
    private static Properties loadExternalProperties() {
        Properties props = new Properties();
        File file = new File("db.properties");
        if (!file.exists()) {
            file = new File(System.getProperty("user.home"), ".crts" + File.separator + "db.properties");
        }
        if (file.exists() && file.isFile()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to read external db.properties: {0}", e.getMessage());
            }
        }
        return props;
    }

    /**
     * Reads an environment variable; returns {@code fallback} when absent
     * or blank.
     */
    private static String resolveEnv(String name, String fallback) {
        String value = System.getenv(name);
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
