package com.priorityqueue.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.DriverManager;

@Slf4j
public class Database {

    private final HikariDataSource dataSource;

    public Database(File dataFolder, Config config) {
        HikariConfig hikariConfig = new HikariConfig();

        String type = config.getDatabaseType() != null ? config.getDatabaseType() : "sqlite";
        String driverClassName = null;

        if ("mysql".equalsIgnoreCase(type)) {
            // MySQL configuration
            String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s",
                    config.getDbHost(),
                    config.getDbPort(),
                    config.getDbDatabase());
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(config.getDbUsername());
            hikariConfig.setPassword(config.getDbPassword());
            driverClassName = "com.mysql.cj.jdbc.Driver";
        } else {
            // SQLite configuration
            String dbPath = new File(dataFolder, "queue.db").getAbsolutePath();
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbPath);
            driverClassName = "org.sqlite.JDBC";
        }

        // Load driver class explicitly
        try {
            Class.forName(driverClassName);
            hikariConfig.setDriverClassName(driverClassName);
        } catch (ClassNotFoundException e) {
            log.error("Failed to load driver class: " + driverClassName, e);
            throw new RuntimeException("Failed to load driver class: " + driverClassName, e);
        }

        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setIdleTimeout(30000);
        hikariConfig.setConnectionTimeout(10000);

        this.dataSource = new HikariDataSource(hikariConfig);

        initializeTables();
    }

    private void initializeTables() {
        try (Connection connection = dataSource.getConnection()) {
            String sql = """
                CREATE TABLE IF NOT EXISTS priority_cache (
                    uuid VARCHAR(36) PRIMARY KEY,
                    priority INTEGER NOT NULL DEFAULT 0,
                    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;

            connection.createStatement().execute(sql);
            log.info("Database tables initialized successfully");
        } catch (SQLException e) {
            log.error("Failed to initialize database tables", e);
        }
    }

    public void savePriority(String uuid, int priority) {
        String sql = """
            INSERT OR REPLACE INTO priority_cache (uuid, priority, last_updated)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            statement.setInt(2, priority);
            statement.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save priority for UUID: " + uuid, e);
        }
    }

    public Integer getPriority(String uuid) {
        String sql = "SELECT priority FROM priority_cache WHERE uuid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                return rs.getInt("priority");
            }
        } catch (SQLException e) {
            log.error("Failed to get priority for UUID: " + uuid, e);
        }

        return null;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("Database connection closed");
        }
    }
}
