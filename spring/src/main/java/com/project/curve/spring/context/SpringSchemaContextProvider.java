package com.project.curve.spring.context;

import com.project.curve.core.context.SchemaContextProvider;
import com.project.curve.core.envelope.EventSchema;

/**
 * Spring 기반 스키마 컨텍스트 제공자
 *
 * TODO: 현재는 기본 스키마 정보만 반환합니다.
 * 향후 개선 방안:
 * 1. SchemaContextProvider 인터페이스에 payload 파라미터 추가
 * 2. payload 클래스명을 기반으로 스키마 이름 생성
 * 3. Schema Registry (Confluent, AWS Glue 등) 연동
 * 4. 어노테이션 기반 스키마 정보 정의 (@EventSchema)
 */
public class SpringSchemaContextProvider implements SchemaContextProvider {

    private static final String DEFAULT_SCHEMA_NAME = "DomainEvent";
    private static final int DEFAULT_SCHEMA_VERSION = 1;

    @Override
    public EventSchema getSchema() {
        // 기본 스키마 정보 반환
        return EventSchema.of(DEFAULT_SCHEMA_NAME, DEFAULT_SCHEMA_VERSION);
    }
}
