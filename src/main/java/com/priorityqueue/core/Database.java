package com.priorityqueue.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Slf4j
public class Database {

    private final HikariDataSource dataSource;

    public Database(File dataFolder, Config config) {
        HikariConfig hikariConfig = new HikariConfig();

        String type = config.getTargetServer() != null ? "sqlite" : "sqlite";

        if ("mysql".equalsIgnoreCase(type)) {
            // MySQL configuration
            hikariConfig.setJdbcUrl("jdbc:mysql://" +
                    config.getTargetServer() + ":3306/priorityqueue");
            hikariConfig.setUsername("root");
            hikariConfig.setPassword("password");
        } else {
            // SQLite configuration
            String dbPath = new File(dataFolder, "queue.db").getAbsolutePath();
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbPath);
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
