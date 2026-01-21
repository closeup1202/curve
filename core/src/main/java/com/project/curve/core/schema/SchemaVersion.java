package com.project.curve.core.schema;

/**
 * 이벤트 스키마 버전 정보를 표현하는 Record.
 * <p>
 * 각 이벤트 페이로드는 특정 스키마 버전을 가지며, 버전 간 마이그레이션을 지원합니다.
 *
 * @param name        스키마 이름 (예: "OrderCreated", "UserRegistered")
 * @param version     스키마 버전 (1부터 시작)
 * @param payloadClass 페이로드 클래스
 */
public record SchemaVersion(
    String name,
    int version,
    Class<?> payloadClass
) {
    /**
     * SchemaVersion을 생성합니다.
     *
     * @param name         스키마 이름
     * @param version      스키마 버전
     * @param payloadClass 페이로드 클래스
     * @throws IllegalArgumentException 이름이 null이거나 빈 문자열이거나, 버전이 1 미만인 경우
     */
    public SchemaVersion {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Schema name must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("Schema version must be >= 1, but was: " + version);
        }
        if (payloadClass == null) {
            throw new IllegalArgumentException("Payload class must not be null");
        }
    }

    /**
     * 스키마의 전체 키를 반환합니다.
     * <p>
     * 형식: {name}:v{version} (예: "OrderCreated:v1")
     *
     * @return 스키마 전체 키
     */
    public String getKey() {
        return name + ":v" + version;
    }

    /**
     * 다른 버전과 비교합니다.
     *
     * @param other 비교할 버전
     * @return 이 버전이 더 크면 양수, 같으면 0, 작으면 음수
     */
    public int compareVersion(SchemaVersion other) {
        if (!this.name.equals(other.name)) {
            throw new IllegalArgumentException(
                "Cannot compare versions of different schemas: " + this.name + " vs " + other.name
            );
        }
        return Integer.compare(this.version, other.version);
    }

    /**
     * 이 버전이 다른 버전보다 최신인지 확인합니다.
     *
     * @param other 비교할 버전
     * @return 이 버전이 더 최신이면 true
     */
    public boolean isNewerThan(SchemaVersion other) {
        return compareVersion(other) > 0;
    }

    /**
     * 이 버전이 다른 버전과 호환 가능한지 확인합니다.
     * <p>
     * 기본적으로 같은 스키마 이름이면 호환 가능한 것으로 간주합니다.
     *
     * @param other 비교할 버전
     * @return 호환 가능하면 true
     */
    public boolean isCompatibleWith(SchemaVersion other) {
        return this.name.equals(other.name);
    }
}
