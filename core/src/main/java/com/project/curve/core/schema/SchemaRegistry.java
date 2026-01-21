package com.project.curve.core.schema;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 이벤트 스키마 버전을 관리하는 레지스트리.
 * <p>
 * 스키마 버전 등록, 조회, 마이그레이션 경로 탐색을 지원합니다.
 * <p>
 * <b>사용 예제:</b>
 * <pre>{@code
 * // 스키마 등록
 * SchemaRegistry registry = new SchemaRegistry();
 * registry.register(new SchemaVersion("OrderCreated", 1, OrderCreatedPayloadV1.class));
 * registry.register(new SchemaVersion("OrderCreated", 2, OrderCreatedPayloadV2.class));
 *
 * // 마이그레이션 등록
 * registry.registerMigration(new OrderCreatedPayloadV1ToV2Migration());
 *
 * // 호환성 확인
 * boolean compatible = registry.isCompatible("OrderCreated", 1, 2);
 *
 * // 최신 버전 조회
 * SchemaVersion latest = registry.getLatestVersion("OrderCreated");
 * }</pre>
 */
public class SchemaRegistry {

    private final Map<String, Map<Integer, SchemaVersion>> schemas = new ConcurrentHashMap<>();
    private final Map<String, SchemaMigration<?, ?>> migrations = new ConcurrentHashMap<>();

    /**
     * 스키마 버전을 등록합니다.
     *
     * @param schemaVersion 등록할 스키마 버전
     * @throws IllegalArgumentException 이미 동일한 버전이 등록된 경우
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
     * 마이그레이션을 등록합니다.
     *
     * @param migration 등록할 마이그레이션
     * @param <FROM>    소스 타입
     * @param <TO>      타겟 타입
     * @throws IllegalArgumentException 마이그레이션의 시작/대상 버전이 등록되지 않은 경우
     */
    public <FROM, TO> void registerMigration(SchemaMigration<FROM, TO> migration) {
        if (migration == null) {
            throw new IllegalArgumentException("SchemaMigration must not be null");
        }

        SchemaVersion from = migration.fromVersion();
        SchemaVersion to = migration.toVersion();

        // 버전 존재 여부 확인
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
     * 특정 스키마의 특정 버전을 조회합니다.
     *
     * @param schemaName 스키마 이름
     * @param version    버전
     * @return 스키마 버전 (존재하지 않으면 Optional.empty())
     */
    public Optional<SchemaVersion> getVersion(String schemaName, int version) {
        return Optional.ofNullable(schemas.get(schemaName))
                       .map(versionMap -> versionMap.get(version));
    }

    /**
     * 특정 스키마의 최신 버전을 조회합니다.
     *
     * @param schemaName 스키마 이름
     * @return 최신 버전 (존재하지 않으면 Optional.empty())
     */
    public Optional<SchemaVersion> getLatestVersion(String schemaName) {
        return Optional.ofNullable(schemas.get(schemaName))
                       .flatMap(versionMap -> versionMap.values().stream()
                           .max(Comparator.comparingInt(SchemaVersion::version)));
    }

    /**
     * 특정 스키마의 모든 등록된 버전을 조회합니다.
     *
     * @param schemaName 스키마 이름
     * @return 등록된 모든 버전 (버전 오름차순 정렬)
     */
    public List<SchemaVersion> getAllVersions(String schemaName) {
        return Optional.ofNullable(schemas.get(schemaName))
                       .map(versionMap -> versionMap.values().stream()
                           .sorted(Comparator.comparingInt(SchemaVersion::version))
                           .toList())
                       .orElse(Collections.emptyList());
    }

    /**
     * 두 버전 간 마이그레이션을 조회합니다.
     *
     * @param from 소스 버전
     * @param to   타겟 버전
     * @return 마이그레이션 (존재하지 않으면 Optional.empty())
     */
    public Optional<SchemaMigration<?, ?>> getMigration(SchemaVersion from, SchemaVersion to) {
        String key = getMigrationKey(from, to);
        return Optional.ofNullable(migrations.get(key));
    }

    /**
     * 두 버전이 호환 가능한지 확인합니다.
     * <p>
     * 호환 가능한 조건:
     * <ul>
     *   <li>같은 스키마 이름</li>
     *   <li>직접 또는 경유 마이그레이션 경로가 존재</li>
     * </ul>
     *
     * @param schemaName  스키마 이름
     * @param fromVersion 소스 버전
     * @param toVersion   타겟 버전
     * @return 호환 가능하면 true
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

        // 마이그레이션 경로 존재 확인
        return findMigrationPath(from.get(), to.get()).isPresent();
    }

    /**
     * 두 버전 간 마이그레이션 경로를 탐색합니다.
     * <p>
     * BFS(너비 우선 탐색)를 사용하여 최단 경로를 찾습니다.
     *
     * @param from 시작 버전
     * @param to   대상 버전
     * @return 마이그레이션 경로 (없으면 Optional.empty())
     */
    public Optional<List<SchemaMigration<?, ?>>> findMigrationPath(SchemaVersion from, SchemaVersion to) {
        if (from.equals(to)) {
            return Optional.of(Collections.emptyList());
        }

        // BFS로 최단 경로 탐색
        Queue<PathNode> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        queue.offer(new PathNode(from, new ArrayList<>()));
        visited.add(from.getKey());

        while (!queue.isEmpty()) {
            PathNode current = queue.poll();

            // 현재 버전에서 갈 수 있는 모든 다음 버전 탐색
            for (int nextVersion = current.version.version() + 1;
                 nextVersion <= to.version();
                 nextVersion++) {

                Optional<SchemaVersion> next = getVersion(from.name(), nextVersion);
                if (next.isEmpty()) continue;

                Optional<SchemaMigration<?, ?>> migration = getMigration(current.version, next.get());
                if (migration.isEmpty()) continue;

                List<SchemaMigration<?, ?>> newPath = new ArrayList<>(current.path);
                newPath.add(migration.get());

                if (next.get().equals(to)) {
                    return Optional.of(newPath);
                }

                if (visited.add(next.get().getKey())) {
                    queue.offer(new PathNode(next.get(), newPath));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * 특정 버전이 등록되어 있는지 확인합니다.
     *
     * @param schemaName 스키마 이름
     * @param version    버전
     * @return 등록되어 있으면 true
     */
    public boolean isVersionRegistered(String schemaName, int version) {
        return getVersion(schemaName, version).isPresent();
    }

    /**
     * 등록된 모든 스키마 이름을 조회합니다.
     *
     * @return 스키마 이름 집합
     */
    public Set<String> getAllSchemaNames() {
        return new HashSet<>(schemas.keySet());
    }

    /**
     * 마이그레이션 키를 생성합니다.
     */
    private String getMigrationKey(SchemaVersion from, SchemaVersion to) {
        return from.getKey() + "->" + to.getKey();
    }

    /**
     * BFS 탐색을 위한 노드 클래스.
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
