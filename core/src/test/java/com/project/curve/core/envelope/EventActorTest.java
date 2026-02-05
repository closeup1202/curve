package com.project.curve.core.envelope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventActor test")
class EventActorTest {

    @Test
    @DisplayName("Create EventActor with valid parameters")
    void createValidEventActor() {
        // given
        String id = "user-123";
        String role = "ROLE_USER";
        String ip = "192.168.1.1";

        // when
        EventActor actor = new EventActor(id, role, ip);

        // then
        assertNotNull(actor);
        assertEquals(id, actor.id());
        assertEquals(role, actor.role());
        assertEquals(ip, actor.ip());
    }

    @Test
    @DisplayName("EventActor - can be created with null values (no validation)")
    void createEventActorWithNullValues() {
        // when
        EventActor actor = new EventActor(null, null, null);

        // then - creation succeeds because there is no validation
        assertNotNull(actor);
        assertNull(actor.id());
        assertNull(actor.role());
        assertNull(actor.ip());
    }
}
