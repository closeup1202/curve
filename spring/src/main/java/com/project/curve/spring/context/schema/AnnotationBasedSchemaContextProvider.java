package com.project.curve.spring.context.schema;

import com.project.curve.core.annotation.PayloadSchema;
import com.project.curve.core.context.SchemaContextProvider;
import com.project.curve.core.envelope.EventSchema;
import com.project.curve.core.payload.DomainEventPayload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 어노테이션 기반 스키마 컨텍스트 제공자
 *
 * <p>페이로드 클래스에 {@link PayloadSchema} 어노테이션이 있으면 해당 정보를 사용하고,
 * 없으면 클래스명을 스키마 이름으로, 버전은 1로 사용합니다.</p>
 *
 * <p>성능을 위해 스키마 정보를 캐싱합니다.</p>
 */
public class AnnotationBasedSchemaContextProvider implements SchemaContextProvider {

    private static final String DEFAULT_SCHEMA_NAME = "DomainEvent";
    private static final int DEFAULT_SCHEMA_VERSION = 1;

    private final Map<Class<?>, EventSchema> schemaCache = new ConcurrentHashMap<>();

    @Override
    public EventSchema getSchema() {
        return EventSchema.of(DEFAULT_SCHEMA_NAME, DEFAULT_SCHEMA_VERSION);
    }

    @Override
    public EventSchema getSchemaFor(DomainEventPayload payload) {
        if (payload == null) {
            return getSchema();
        }

        return schemaCache.computeIfAbsent(payload.getClass(), this::resolveSchema);
    }

    private EventSchema resolveSchema(Class<?> payloadClass) {
        PayloadSchema annotation = payloadClass.getAnnotation(PayloadSchema.class);

        if (annotation == null) {
            return EventSchema.of(payloadClass.getSimpleName(), DEFAULT_SCHEMA_VERSION);
        }

        String name = annotation.name().isBlank()
                ? payloadClass.getSimpleName()
                : annotation.name();

        String schemaId = annotation.schemaId().isBlank()
                ? null
                : annotation.schemaId();

        return new EventSchema(name, annotation.version(), schemaId);
    }
}
