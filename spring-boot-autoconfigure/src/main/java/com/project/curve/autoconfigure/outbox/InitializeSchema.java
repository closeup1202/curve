package com.project.curve.autoconfigure.outbox;

/**
 * Outbox table schema initialization mode.
 * <p>
 * Follows the same pattern as Spring Batch's {@code spring.batch.jdbc.initialize-schema}.
 */
public enum InitializeSchema {

    /**
     * Automatically create only for embedded databases (H2, HSQLDB, Derby).
     */
    EMBEDDED,

    /**
     * Always automatically create (CREATE only when table does not exist).
     */
    ALWAYS,

    /**
     * Do not automatically create. Managed directly by the service using Flyway/Liquibase, etc.
     */
    NEVER
}
