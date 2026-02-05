package com.project.curve.core.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OutboxStatus test")
class OutboxStatusTest {

    @Test
    @DisplayName("OutboxStatus enum values verification")
    void testOutboxStatusValues() {
        // when
        OutboxStatus[] statuses = OutboxStatus.values();

        // then
        assertEquals(3, statuses.length);
        assertEquals(OutboxStatus.PENDING, statuses[0]);
        assertEquals(OutboxStatus.PUBLISHED, statuses[1]);
        assertEquals(OutboxStatus.FAILED, statuses[2]);
    }

    @Test
    @DisplayName("OutboxStatus valueOf test")
    void testValueOf() {
        // when & then
        assertEquals(OutboxStatus.PENDING, OutboxStatus.valueOf("PENDING"));
        assertEquals(OutboxStatus.PUBLISHED, OutboxStatus.valueOf("PUBLISHED"));
        assertEquals(OutboxStatus.FAILED, OutboxStatus.valueOf("FAILED"));
    }

    @Test
    @DisplayName("OutboxStatus comparison test")
    void testComparison() {
        // given
        OutboxStatus pending = OutboxStatus.PENDING;
        OutboxStatus published = OutboxStatus.PUBLISHED;
        OutboxStatus failed = OutboxStatus.FAILED;

        // then
        assertNotEquals(pending, published);
        assertNotEquals(pending, failed);
        assertNotEquals(published, failed);
        assertEquals(pending, OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("OutboxStatus toString test")
    void testToString() {
        // then
        assertEquals("PENDING", OutboxStatus.PENDING.toString());
        assertEquals("PUBLISHED", OutboxStatus.PUBLISHED.toString());
        assertEquals("FAILED", OutboxStatus.FAILED.toString());
    }

    @Test
    @DisplayName("OutboxStatus name method test")
    void testName() {
        // then
        assertEquals("PENDING", OutboxStatus.PENDING.name());
        assertEquals("PUBLISHED", OutboxStatus.PUBLISHED.name());
        assertEquals("FAILED", OutboxStatus.FAILED.name());
    }

    @Test
    @DisplayName("OutboxStatus ordinal test")
    void testOrdinal() {
        // then
        assertEquals(0, OutboxStatus.PENDING.ordinal());
        assertEquals(1, OutboxStatus.PUBLISHED.ordinal());
        assertEquals(2, OutboxStatus.FAILED.ordinal());
    }

    @Test
    @DisplayName("Throws exception when valueOf is called with invalid value")
    void testInvalidValueOf() {
        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                OutboxStatus.valueOf("INVALID_STATUS")
        );
    }
}
