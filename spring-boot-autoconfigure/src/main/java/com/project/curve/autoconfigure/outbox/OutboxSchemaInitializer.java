package com.project.curve.autoconfigure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Set;

/**
 * Outbox table auto-creation initializer.
 * <p>
 * Automatically creates the curve_outbox_events table based on the {@link InitializeSchema} mode.
 *
 * <h3>Supported Databases</h3>
 * <ul>
 *   <li>MySQL / MariaDB</li>
 *   <li>PostgreSQL</li>
 *   <li>H2 / HSQLDB / Derby (embedded)</li>
 * </ul>
 *
 * @see InitializeSchema
 * @see CurveOutboxAutoConfiguration
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxSchemaInitializer implements InitializingBean {

    private static final String TABLE_NAME = "curve_outbox_events";

    private static final Set<String> EMBEDDED_DATABASES = Set.of("h2", "hsql", "derby", "sqlite");

    private final DataSource dataSource;
    private final InitializeSchema mode;

    @Override
    public void afterPropertiesSet() throws Exception {
        if (mode == InitializeSchema.NEVER) {
            log.debug("Outbox schema initialization is disabled (initialize-schema=never)");
            return;
        }

        try (Connection connection = dataSource.getConnection()) {
            String dbName = connection.getMetaData().getDatabaseProductName().toLowerCase();

            if (mode == InitializeSchema.EMBEDDED && !isEmbeddedDatabase(dbName)) {
                log.debug("Skipping outbox schema initialization for non-embedded database '{}' " +
                        "(initialize-schema=embedded)", dbName);
                return;
            }

            if (tableExists(connection)) {
                log.debug("Outbox table '{}' already exists, skipping creation", TABLE_NAME);
                return;
            }

            String ddl = generateCreateTableDdl(dbName);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute(ddl);
            log.info("Outbox table '{}' created successfully (mode={})", TABLE_NAME, mode);

            createIndexes(jdbcTemplate, dbName);
        } catch (Exception e) {
            log.warn("Failed to auto-create outbox table '{}': {}", TABLE_NAME, e.getMessage());
            throw e;
        }
    }

    private boolean isEmbeddedDatabase(String dbName) {
        return EMBEDDED_DATABASES.stream().anyMatch(dbName::contains);
    }

    private boolean tableExists(Connection connection) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getTables(null, null, TABLE_NAME, new String[]{"TABLE"})) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = metaData.getTables(null, null, TABLE_NAME.toUpperCase(), new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private String generateCreateTableDdl(String dbName) {
        if (dbName.contains("postgresql")) {
            return createPostgresqlDdl();
        }
        if (dbName.contains("mysql") || dbName.contains("mariadb")) {
            return createMysqlDdl();
        }
        if (dbName.contains("oracle")) {
            return createOracleDdl();
        }
        if (dbName.contains("sqlite")) {
            return createSqliteDdl();
        }
        return createStandardDdl();
    }

    private String createPostgresqlDdl() {
        return """
                CREATE TABLE IF NOT EXISTS curve_outbox_events (
                    event_id        VARCHAR(64)     NOT NULL PRIMARY KEY,
                    aggregate_type  VARCHAR(100)    NOT NULL,
                    aggregate_id    VARCHAR(100)    NOT NULL,
                    event_type      VARCHAR(100)    NOT NULL,
                    payload         TEXT            NOT NULL,
                    occurred_at     TIMESTAMP       NOT NULL,
                    status          VARCHAR(20)     NOT NULL,
                    retry_count     INT             NOT NULL DEFAULT 0,
                    published_at    TIMESTAMP,
                    error_message   VARCHAR(500),
                    next_retry_at   TIMESTAMP,
                    created_at      TIMESTAMP       NOT NULL,
                    updated_at      TIMESTAMP       NOT NULL,
                    version         BIGINT
                )""";
    }

    private String createMysqlDdl() {
        return """
                CREATE TABLE IF NOT EXISTS curve_outbox_events (
                    event_id        VARCHAR(64)     NOT NULL,
                    aggregate_type  VARCHAR(100)    NOT NULL,
                    aggregate_id    VARCHAR(100)    NOT NULL,
                    event_type      VARCHAR(100)    NOT NULL,
                    payload         TEXT            NOT NULL,
                    occurred_at     TIMESTAMP(6)    NOT NULL,
                    status          VARCHAR(20)     NOT NULL,
                    retry_count     INT             NOT NULL DEFAULT 0,
                    published_at    TIMESTAMP(6)    NULL,
                    error_message   VARCHAR(500),
                    next_retry_at   TIMESTAMP(6)    NULL,
                    created_at      TIMESTAMP(6)    NOT NULL,
                    updated_at      TIMESTAMP(6)    NOT NULL,
                    version         BIGINT,
                    PRIMARY KEY (event_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""";
    }

    private String createOracleDdl() {
        return """
                CREATE TABLE curve_outbox_events (
                    event_id        VARCHAR2(64)    NOT NULL PRIMARY KEY,
                    aggregate_type  VARCHAR2(100)   NOT NULL,
                    aggregate_id    VARCHAR2(100)   NOT NULL,
                    event_type      VARCHAR2(100)   NOT NULL,
                    payload         CLOB            NOT NULL,
                    occurred_at     TIMESTAMP       NOT NULL,
                    status          VARCHAR2(20)    NOT NULL,
                    retry_count     NUMBER(10)      DEFAULT 0 NOT NULL,
                    published_at    TIMESTAMP,
                    error_message   VARCHAR2(500),
                    next_retry_at   TIMESTAMP,
                    created_at      TIMESTAMP       NOT NULL,
                    updated_at      TIMESTAMP       NOT NULL,
                    version         NUMBER(19)
                )""";
    }

    private String createSqliteDdl() {
        return """
                CREATE TABLE IF NOT EXISTS curve_outbox_events (
                    event_id        TEXT        NOT NULL PRIMARY KEY,
                    aggregate_type  TEXT        NOT NULL,
                    aggregate_id    TEXT        NOT NULL,
                    event_type      TEXT        NOT NULL,
                    payload         TEXT        NOT NULL,
                    occurred_at     TEXT        NOT NULL,
                    status          TEXT        NOT NULL,
                    retry_count     INTEGER     NOT NULL DEFAULT 0,
                    published_at    TEXT,
                    error_message   TEXT,
                    next_retry_at   TEXT,
                    created_at      TEXT        NOT NULL,
                    updated_at      TEXT        NOT NULL,
                    version         INTEGER
                )""";
    }

    private String createStandardDdl() {
        return """
                CREATE TABLE IF NOT EXISTS curve_outbox_events (
                    event_id        VARCHAR(64)     NOT NULL PRIMARY KEY,
                    aggregate_type  VARCHAR(100)    NOT NULL,
                    aggregate_id    VARCHAR(100)    NOT NULL,
                    event_type      VARCHAR(100)    NOT NULL,
                    payload         TEXT            NOT NULL,
                    occurred_at     TIMESTAMP       NOT NULL,
                    status          VARCHAR(20)     NOT NULL,
                    retry_count     INT             NOT NULL DEFAULT 0,
                    published_at    TIMESTAMP,
                    error_message   VARCHAR(500),
                    next_retry_at   TIMESTAMP,
                    created_at      TIMESTAMP       NOT NULL,
                    updated_at      TIMESTAMP       NOT NULL,
                    version         BIGINT
                )""";
    }

    private void createIndexes(JdbcTemplate jdbcTemplate, String dbName) {
        createIndex(jdbcTemplate, dbName, "idx_outbox_status", "status");
        createIndex(jdbcTemplate, dbName, "idx_outbox_aggregate", "aggregate_type, aggregate_id");
        createIndex(jdbcTemplate, dbName, "idx_outbox_occurred_at", "occurred_at");
        createIndex(jdbcTemplate, dbName, "idx_outbox_next_retry", "next_retry_at");
    }

    private void createIndex(JdbcTemplate jdbcTemplate, String dbName, String indexName, String columns) {
        try {
            String ddl;
            if (dbName.contains("mysql") || dbName.contains("mariadb") || dbName.contains("oracle")) {
                // MySQL, MariaDB, Oracle: CREATE INDEX IF NOT EXISTS not supported
                ddl = String.format("CREATE INDEX %s ON %s (%s)", indexName, TABLE_NAME, columns);
            } else {
                // PostgreSQL, H2, SQLite: IF NOT EXISTS supported
                ddl = String.format("CREATE INDEX IF NOT EXISTS %s ON %s (%s)", indexName, TABLE_NAME, columns);
            }
            jdbcTemplate.execute(ddl);
            log.debug("Index '{}' created on '{}'", indexName, TABLE_NAME);
        } catch (Exception e) {
            log.debug("Index '{}' creation skipped (may already exist): {}", indexName, e.getMessage());
        }
    }
}
