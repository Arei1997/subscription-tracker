package com.tracker.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitialiser {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitialiser.class);

    private final String url;

    public DatabaseInitialiser(String url) {
        this.url = url;
    }

    public void initialise() {
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS subscriptions (
                        id             INTEGER PRIMARY KEY AUTOINCREMENT,
                        name           TEXT    NOT NULL,
                        category       TEXT    NOT NULL,
                        cost           REAL    NOT NULL,
                        billing_cycle  TEXT    NOT NULL,
                        start_date     TEXT    NOT NULL,
                        renewal_date   TEXT    NOT NULL,
                        status         TEXT    NOT NULL DEFAULT 'ACTIVE',
                        cancelled_date TEXT,
                        notes          TEXT,
                        created_at     TEXT    NOT NULL
                    )
                    """);

            log.info("Database initialised at {}", url);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialise database", e);
        }
    }
}
