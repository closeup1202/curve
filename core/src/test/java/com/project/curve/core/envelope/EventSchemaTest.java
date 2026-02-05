package com.project.curve.core.envelope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventSchema test")
class EventSchemaTest {

    @Test
    @DisplayName("Create EventSchema with of() factory method")
    void createEventSchemaUsingFactory() {
        // given
        String name = "OrderCreatedEvent";
        int version = 1;

        // when
        EventSchema schema = EventSchema.of(name, version);

        // then
        assertNotNull(schema);
        assertEquals(name, schema.name());
        assertEquals(version, schema.version());
        assertNull(schema.schemaId());
    }

    @Test
    @DisplayName("Create EventSchema with constructor including schemaId")
    void createEventSchemaWithSchemaId() {
        // given
        String name = "OrderCreatedEvent";
        int version = 2;
        String schemaId = "schema-registry-id-123";

        // when
        EventSchema schema = new EventSchema(name, version, schemaId);

        // then
        assertNotNull(schema);
        assertEquals(name, schema.name());
        assertEquals(version, schema.version());
        assertEquals(schemaId, schema.schemaId());
    }

    @Test
    @DisplayName("EventSchema creation fails when name is null")
    void createEventSchemaWithNullName_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> EventSchema.of(null, 1)
        );
        assertEquals("schema.name must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("EventSchema creation fails when name is empty string")
    void createEventSchemaWithEmptyName_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> EventSchema.of("", 1)
        );
        assertEquals("schema.name must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("EventSchema creation fails when version is 0")
    void createEventSchemaWithZeroVersion_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> EventSchema.of("TestEvent", 0)
        );
        assertEquals("schema.version must be positive", exception.getMessage());
    }

    @Test
    @DisplayName("EventSchema creation fails when version is negative")
    void createEventSchemaWithNegativeVersion_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> EventSchema.of("TestEvent", -1)
        );
        assertEquals("schema.version must be positive", exception.getMessage());
    }
}
