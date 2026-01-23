package com.project.curve.autoconfigure.outbox;

/**
 * Outbox 테이블 스키마 초기화 모드.
 * <p>
 * Spring Batch의 {@code spring.batch.jdbc.initialize-schema}와 동일한 패턴을 따릅니다.
 */
public enum InitializeSchema {

    /**
     * 임베디드 데이터베이스(H2, HSQLDB, Derby)에서만 자동 생성.
     */
    EMBEDDED,

    /**
     * 항상 자동 생성 (테이블이 없을 때만 CREATE).
     */
    ALWAYS,

    /**
     * 자동 생성하지 않음. 서비스에서 Flyway/Liquibase 등으로 직접 관리.
     */
    NEVER
}
