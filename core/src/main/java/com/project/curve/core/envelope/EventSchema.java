package com.project.curve.core.envelope;

/**
 * 이벤트 스키마 정보.
 * <p>
 * 이벤트의 구조와 버전을 정의합니다.
 * 스키마 진화(Schema Evolution)를 관리하고 하위 호환성을 유지하는 데 사용됩니다.
 *
 * @param name     스키마 이름 (예: "OrderCreatedEvent")
 * @param version  스키마 버전 (1부터 시작하여 순차적으로 증가)
 * @param schemaId 외부 스키마 레지스트리(Schema Registry) 연동 시 사용되는 고유 ID (선택사항)
 */
public record EventSchema(
        String name,
        int version,
        String schemaId
) {
    public EventSchema {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("schema.name must not be blank");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("schema.version must be positive");
        }
    }

    public static EventSchema of(String name, int version) {
        return new EventSchema(name, version, null);
    }
}
