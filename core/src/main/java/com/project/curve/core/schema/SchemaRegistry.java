package com.project.curve.core.schema;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing event schema versions.
 * <p>
 * Supports schema version registration, retrieval, and migration path discovery.
 * <p>
 * <b>Usage Example:</b>
 * <pre>{@code
 * // Register schemas
 * SchemaRegistry registry = new SchemaRegistry();
 * registry.register(new SchemaVersion("OrderCreated", 1, OrderCreatedPayloadV1.class));
 * registry.register(new SchemaVersion("OrderCreated", 2, OrderCreatedPayloadV2.class));
 *
 * // Register migration
 * registry.registerMigration(new OrderCreatedPayloadV1ToV2Migration());
 *
 * // Check compatibility
 * boolean compatible = registry.isCompatible("OrderCreated", 1, 2);
 *
 * // Retrieve latest version
 * SchemaVersion latest = registry.getLatestVersion("OrderCreated");
 * }</pre>
 */
public class SchemaRegistry {

    private final Map<String, Map<Integer, SchemaVersion>> schemas = new ConcurrentHashMap<>();
    private final Map<String, SchemaMigration<?, ?>> migrations = new ConcurrentHashMap<>();

    /**
     * Registers a schema version.
     *
     * @param schemaVersion the schema version to register
     * @throws IllegalArgumentException if the same version is already registered
     */
    public void register(SchemaVersion schemaVersion) {
        if (schemaVersion == null) {
            throw new IllegalArgumentException("SchemaVersion must not be null");
        }

        schemas.computeIfAbsent(schemaVersion.name(), k -> new ConcurrentHashMap<>())
               .compute(schemaVersion.version(), (v, existing) -> {
                   if (existing != null && !existing.payloadClass().equals(schemaVersion.payloadClass())) {
                       throw new IllegalArgumentException(
                           "Schema version already registered with different payload class: " +
                           schemaVersion.getKey()
                       );
                   }
                   return schemaVersion;
               });
    }

    /**
     * Registers a migration.
     *
     * @param migration the migration to register
     * @param <FROM>    source type
     * @param <TO>      target type
     * @throws IllegalArgumentException if the migration's source or target version is not registered
     */
    public <FROM, TO> void registerMigration(SchemaMigration<FROM, TO> migration) {
        if (migration == null) {
            throw new IllegalArgumentException("SchemaMigration must not be null");
        }

        SchemaVersion from = migration.fromVersion();
        SchemaVersion to = migration.toVersion();

        // Check if versions exist
        if (!isVersionRegistered(from.name(), from.version())) {
            throw new IllegalArgumentException(
                "Source schema version not registered: " + from.getKey()
            );
        }
        if (!isVersionRegistered(to.name(), to.version())) {
            throw new IllegalArgumentException(
                "Target schema version not registered: " + to.getKey()
            );
        }

        String migrationKey = getMigrationKey(from, to);
        migrations.put(migrationKey, migration);
    }

    /**
     * Retrieves a specific version of a schema.
     *
     * @param schemaName the schema name
     * @param version    the version
     * @return the schema version (Optional.empty() if not found)
     */
    public Optional<SchemaVersion> getVersion(String schemaName, int version) {
        return Optional.ofNullable(schemas.get(schemaName))
                       .map(versionMap -> versionMap.get(version));
    }

    /**
     * Retrieves the latest version of a schema.
     *
     * @param schemaName the schema name
     * @return the latest version (Optional.empty() if not found)
     */
    public Optional<SchemaVersion> getLatestVersion(String schemaName) {
        return Optional.ofNullable(schemas.get(schemaName))
                       .flatMap(versionMap -> versionMap.values().stream()
                           .max(Comparator.comparingInt(SchemaVersion::version)));
    }

    /**
     * Retrieves all registered versions of a schema.
     *
     * @param schemaName the schema name
     * @return all registered versions (sorted in ascending order by version)
     */
    public List<SchemaVersion> getAllVersions(String schemaName) {
        return Optional.ofNullable(schemas.get(schemaName))
                       .map(versionMap -> versionMap.values().stream()
                           .sorted(Comparator.comparingInt(SchemaVersion::version))
                           .toList())
                       .orElse(Collections.emptyList());
    }

    /**
     * Retrieves the migration between two versions.
     *
     * @param from the source version
     * @param to   the target version
     * @return the migration (Optional.empty() if not found)
     */
    public Optional<SchemaMigration<?, ?>> getMigration(SchemaVersion from, SchemaVersion to) {
        String key = getMigrationKey(from, to);
        return Optional.ofNullable(migrations.get(key));
    }

    /**
     * Checks if two versions are compatible.
     * <p>
     * Compatibility conditions:
     * <ul>
     *   <li>Same schema name</li>
     *   <li>Direct or indirect migration path exists</li>
     * </ul>
     *
     * @param schemaName  the schema name
     * @param fromVersion the source version
     * @param toVersion   the target version
     * @return true if compatible
     */
    public boolean isCompatible(String schemaName, int fromVersion, int toVersion) {
        Optional<SchemaVersion> from = getVersion(schemaName, fromVersion);
        Optional<SchemaVersion> to = getVersion(schemaName, toVersion);

        if (from.isEmpty() || to.isEmpty()) {
            return false;
        }

        if (fromVersion == toVersion) {
            return true;
        }

        // Check if migration path exists
        return findMigrationPath(from.get(), to.get()).isPresent();
    }

    /**
     * Finds the migration path between two versions.
     * <p>
     * Uses BFS (Breadth-First Search) to find the shortest path.
     *
     * @param from the starting version
     * @param to   the target version
     * @return the migration path (Optional.empty() if none exists)
     */
    public Optional<List<SchemaMigration<?, ?>>> findMigrationPath(SchemaVersion from, SchemaVersion to) {
        if (from.equals(to)) {
            return Optional.of(Collections.emptyList());
        }

        Queue<PathNode> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        initializeBfsSearch(queue, visited, from);

        return searchMigrationPath(queue, visited, from.name(), to);
    }

    /**
     * Initializes BFS search with starting node.
     */
    private void initializeBfsSearch(Queue<PathNode> queue, Set<String> visited, SchemaVersion startVersion) {
        queue.offer(new PathNode(startVersion, new ArrayList<>()));
        visited.add(startVersion.getKey());
    }

    /**
     * Searches for migration path using BFS.
     */
    private Optional<List<SchemaMigration<?, ?>>> searchMigrationPath(
            Queue<PathNode> queue,
            Set<String> visited,
            String schemaName,
            SchemaVersion targetVersion) {

        while (!queue.isEmpty()) {
            PathNode current = queue.poll();

            Optional<List<SchemaMigration<?, ?>>> result = exploreNextVersions(
                current, queue, visited, schemaName, targetVersion
            );

            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    /**
     * Explores all next versions reachable from current version.
     */
    private Optional<List<SchemaMigration<?, ?>>> exploreNextVersions(
            PathNode current,
            Queue<PathNode> queue,
            Set<String> visited,
            String schemaName,
            SchemaVersion targetVersion) {

        for (int nextVersion = current.version.version() + 1;
             nextVersion <= targetVersion.version();
             nextVersion++) {

            Optional<List<SchemaMigration<?, ?>>> result = tryMigrateToVersion(
                current, queue, visited, schemaName, nextVersion, targetVersion
            );

            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    /**
     * Attempts to migrate to a specific version.
     */
    private Optional<List<SchemaMigration<?, ?>>> tryMigrateToVersion(
            PathNode current,
            Queue<PathNode> queue,
            Set<String> visited,
            String schemaName,
            int nextVersionNumber,
            SchemaVersion targetVersion) {

        Optional<SchemaVersion> nextVersion = getVersion(schemaName, nextVersionNumber);
        if (nextVersion.isEmpty()) {
            return Optional.empty();
        }

        Optional<SchemaMigration<?, ?>> migration = getMigration(current.version, nextVersion.get());
        if (migration.isEmpty()) {
            return Optional.empty();
        }

        List<SchemaMigration<?, ?>> newPath = buildNewPath(current.path, migration.get());

        if (nextVersion.get().equals(targetVersion)) {
            return Optional.of(newPath);
        }

        enqueueIfNotVisited(queue, visited, nextVersion.get(), newPath);
        return Optional.empty();
    }

    /**
     * Builds new migration path by adding migration step.
     */
    private List<SchemaMigration<?, ?>> buildNewPath(
            List<SchemaMigration<?, ?>> currentPath,
            SchemaMigration<?, ?> migration) {
        List<SchemaMigration<?, ?>> newPath = new ArrayList<>(currentPath);
        newPath.add(migration);
        return newPath;
    }

    /**
     * Enqueues next version if not visited.
     */
    private void enqueueIfNotVisited(
            Queue<PathNode> queue,
            Set<String> visited,
            SchemaVersion nextVersion,
            List<SchemaMigration<?, ?>> path) {
        if (visited.add(nextVersion.getKey())) {
            queue.offer(new PathNode(nextVersion, path));
        }
    }

    /**
     * Checks if a specific version is registered.
     *
     * @param schemaName the schema name
     * @param version    the version
     * @return true if registered
     */
    public boolean isVersionRegistered(String schemaName, int version) {
        return getVersion(schemaName, version).isPresent();
    }

    /**
     * Retrieves all registered schema names.
     *
     * @return set of schema names
     */
    public Set<String> getAllSchemaNames() {
        return new HashSet<>(schemas.keySet());
    }

    /**
     * Generates a migration key.
     */
    private String getMigrationKey(SchemaVersion from, SchemaVersion to) {
        return from.getKey() + "->" + to.getKey();
    }

    /**
     * Node class for BFS traversal.
     */
    private static class PathNode {
        final SchemaVersion version;
        final List<SchemaMigration<?, ?>> path;

        PathNode(SchemaVersion version, List<SchemaMigration<?, ?>> path) {
            this.version = version;
            this.path = path;
        }
    }
}
