package com.project.curve.core.schema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SchemaRegistry test")
class SchemaRegistryTest {

    static class PayloadV1 {}
    static class PayloadV2 {}
    static class PayloadV3 {}

    private SchemaRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SchemaRegistry();
    }

    @Test
    @DisplayName("Schema registration test")
    void testRegister() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);

        // when
        registry.register(v1);
        Optional<SchemaVersion> result = registry.getVersion("OrderCreated", 1);

        // then
        assertTrue(result.isPresent());
        assertEquals(v1, result.get());
    }

    @Test
    @DisplayName("Throws exception when registering null schema")
    void testRegister_withNull_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(null)
        );
        assertEquals("SchemaVersion must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("Throws exception when re-registering same version with different class")
    void testRegister_samVersionDifferentClass_shouldThrowException() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        SchemaVersion v1Different = new SchemaVersion("OrderCreated", 1, PayloadV2.class);
        registry.register(v1);

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(v1Different)
        );
        assertTrue(exception.getMessage().contains("already registered with different payload class"));
    }

    @Test
    @DisplayName("Re-registering same version with same class is allowed")
    void testRegister_sameVersionSameClass() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        registry.register(v1);

        // when & then
        assertDoesNotThrow(() -> registry.register(v1));
    }

    @Test
    @DisplayName("Migration registration test")
    void testRegisterMigration() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, PayloadV2.class);
        registry.register(v1);
        registry.register(v2);

        SchemaMigration<PayloadV1, PayloadV2> migration = new SchemaMigration<>() {
            @Override
            public PayloadV2 migrate(PayloadV1 source) {
                return new PayloadV2();
            }

            @Override
            public SchemaVersion fromVersion() {
                return v1;
            }

            @Override
            public SchemaVersion toVersion() {
                return v2;
            }
        };

        // when
        registry.registerMigration(migration);
        Optional<SchemaMigration<?, ?>> result = registry.getMigration(v1, v2);

        // then
        assertTrue(result.isPresent());
        assertEquals(migration, result.get());
    }

    @Test
    @DisplayName("Throws exception when registering null migration")
    void testRegisterMigration_withNull_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.registerMigration(null)
        );
        assertEquals("SchemaMigration must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("Throws exception when registering migration with unregistered source version")
    void testRegisterMigration_sourceNotRegistered_shouldThrowException() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, PayloadV2.class);
        registry.register(v2); // do not register v1

        SchemaMigration<PayloadV1, PayloadV2> migration = new SchemaMigration<>() {
            @Override
            public PayloadV2 migrate(PayloadV1 source) {
                return new PayloadV2();
            }

            @Override
            public SchemaVersion fromVersion() {
                return v1;
            }

            @Override
            public SchemaVersion toVersion() {
                return v2;
            }
        };

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.registerMigration(migration)
        );
        assertTrue(exception.getMessage().contains("Source schema version not registered"));
    }

    @Test
    @DisplayName("Throws exception when registering migration with unregistered target version")
    void testRegisterMigration_targetNotRegistered_shouldThrowException() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, PayloadV2.class);
        registry.register(v1); // do not register v2

        SchemaMigration<PayloadV1, PayloadV2> migration = new SchemaMigration<>() {
            @Override
            public PayloadV2 migrate(PayloadV1 source) {
                return new PayloadV2();
            }

            @Override
            public SchemaVersion fromVersion() {
                return v1;
            }

            @Override
            public SchemaVersion toVersion() {
                return v2;
            }
        };

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.registerMigration(migration)
        );
        assertTrue(exception.getMessage().contains("Target schema version not registered"));
    }

    @Test
    @DisplayName("getLatestVersion test")
    void testGetLatestVersion() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, PayloadV2.class);
        SchemaVersion v3 = new SchemaVersion("OrderCreated", 3, PayloadV3.class);
        registry.register(v1);
        registry.register(v3);
        registry.register(v2);

        // when
        Optional<SchemaVersion> latest = registry.getLatestVersion("OrderCreated");

        // then
        assertTrue(latest.isPresent());
        assertEquals(v3, latest.get());
    }

    @Test
    @DisplayName("getLatestVersion for non-existent schema")
    void testGetLatestVersion_notExists() {
        // when
        Optional<SchemaVersion> latest = registry.getLatestVersion("NonExistent");

        // then
        assertFalse(latest.isPresent());
    }

    @Test
    @DisplayName("getAllVersions test")
    void testGetAllVersions() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, PayloadV2.class);
        SchemaVersion v3 = new SchemaVersion("OrderCreated", 3, PayloadV3.class);
        registry.register(v3);
        registry.register(v1);
        registry.register(v2);

        // when
        List<SchemaVersion> versions = registry.getAllVersions("OrderCreated");

        // then
        assertEquals(3, versions.size());
        assertEquals(v1, versions.get(0)); // sorted order
        assertEquals(v2, versions.get(1));
        assertEquals(v3, versions.get(2));
    }

    @Test
    @DisplayName("getAllVersions for non-existent schema")
    void testGetAllVersions_notExists() {
        // when
        List<SchemaVersion> versions = registry.getAllVersions("NonExistent");

        // then
        assertTrue(versions.isEmpty());
    }

    @Test
    @DisplayName("isCompatible test - same version")
    void testIsCompatible_sameVersion() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        registry.register(v1);

        // when
        boolean compatible = registry.isCompatible("OrderCreated", 1, 1);

        // then
        assertTrue(compatible);
    }

    @Test
    @DisplayName("isCompatible test - migration path exists")
    void testIsCompatible_withMigrationPath() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, PayloadV2.class);
        registry.register(v1);
        registry.register(v2);

        SchemaMigration<PayloadV1, PayloadV2> migration = createMigration(v1, v2);
        registry.registerMigration(migration);

        // when
        boolean compatible = registry.isCompatible("OrderCreated", 1, 2);

        // then
        assertTrue(compatible);
    }

    @Test
    @DisplayName("isCompatible test - no migration path")
    void testIsCompatible_noMigrationPath() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, PayloadV2.class);
        registry.register(v1);
        registry.register(v2);
        // do not register migration

        // when
        boolean compatible = registry.isCompatible("OrderCreated", 1, 2);

        // then
        assertFalse(compatible);
    }

    @Test
    @DisplayName("isCompatible test - version does not exist")
    void testIsCompatible_versionNotExists() {
        // when
        boolean compatible = registry.isCompatible("OrderCreated", 1, 2);

        // then
        assertFalse(compatible);
    }

    @Test
    @DisplayName("findMigrationPath test - same version")
    void testFindMigrationPath_sameVersion() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        registry.register(v1);

        // when
        Optional<List<SchemaMigration<?, ?>>> path = registry.findMigrationPath(v1, v1);

        // then
        assertTrue(path.isPresent());
        assertTrue(path.get().isEmpty());
    }

    @Test
    @DisplayName("findMigrationPath test - direct migration")
    void testFindMigrationPath_directMigration() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, PayloadV2.class);
        registry.register(v1);
        registry.register(v2);

        SchemaMigration<PayloadV1, PayloadV2> migration = createMigration(v1, v2);
        registry.registerMigration(migration);

        // when
        Optional<List<SchemaMigration<?, ?>>> path = registry.findMigrationPath(v1, v2);

        // then
        assertTrue(path.isPresent());
        assertEquals(1, path.get().size());
        assertEquals(migration, path.get().get(0));
    }

    @Test
    @DisplayName("findMigrationPath test - multi-step migration")
    void testFindMigrationPath_multiStep() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, PayloadV2.class);
        SchemaVersion v3 = new SchemaVersion("OrderCreated", 3, PayloadV3.class);
        registry.register(v1);
        registry.register(v2);
        registry.register(v3);

        SchemaMigration<PayloadV1, PayloadV2> migration1 = createMigration(v1, v2);
        SchemaMigration<PayloadV2, PayloadV3> migration2 = createMigration(v2, v3);
        registry.registerMigration(migration1);
        registry.registerMigration(migration2);

        // when
        Optional<List<SchemaMigration<?, ?>>> path = registry.findMigrationPath(v1, v3);

        // then
        assertTrue(path.isPresent());
        assertEquals(2, path.get().size());
        assertEquals(migration1, path.get().get(0));
        assertEquals(migration2, path.get().get(1));
    }

    @Test
    @DisplayName("findMigrationPath test - no path")
    void testFindMigrationPath_noPath() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        SchemaVersion v2 = new SchemaVersion("OrderCreated", 2, PayloadV2.class);
        registry.register(v1);
        registry.register(v2);
        // do not register migration

        // when
        Optional<List<SchemaMigration<?, ?>>> path = registry.findMigrationPath(v1, v2);

        // then
        assertFalse(path.isPresent());
    }

    @Test
    @DisplayName("isVersionRegistered test")
    void testIsVersionRegistered() {
        // given
        SchemaVersion v1 = new SchemaVersion("OrderCreated", 1, PayloadV1.class);
        registry.register(v1);

        // then
        assertTrue(registry.isVersionRegistered("OrderCreated", 1));
        assertFalse(registry.isVersionRegistered("OrderCreated", 2));
        assertFalse(registry.isVersionRegistered("NonExistent", 1));
    }

    @Test
    @DisplayName("getAllSchemaNames test")
    void testGetAllSchemaNames() {
        // given
        registry.register(new SchemaVersion("OrderCreated", 1, PayloadV1.class));
        registry.register(new SchemaVersion("UserRegistered", 1, PayloadV1.class));

        // when
        Set<String> names = registry.getAllSchemaNames();

        // then
        assertEquals(2, names.size());
        assertTrue(names.contains("OrderCreated"));
        assertTrue(names.contains("UserRegistered"));
    }

    private <FROM, TO> SchemaMigration<FROM, TO> createMigration(SchemaVersion from, SchemaVersion to) {
        return new SchemaMigration<FROM, TO>() {
            @Override
            public TO migrate(FROM source) {
                return null;
            }

            @Override
            public SchemaVersion fromVersion() {
                return from;
            }

            @Override
            public SchemaVersion toVersion() {
                return to;
            }
        };
    }
}
