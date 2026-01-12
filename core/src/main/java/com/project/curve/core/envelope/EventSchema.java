package com.project.curve.core.envelope;

public record EventSchema(
        String name,    // 예: "OrderCreatedEvent"
        int version,    // 예: 1, 2, 3 (순차적 증가)
        String schemaId // 선택사항: 외부 스키마 레지스트리 연동 시 ID
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
