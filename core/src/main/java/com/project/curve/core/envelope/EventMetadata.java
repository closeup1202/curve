package com.project.curve.core.envelope;

import java.util.Collections;
import java.util.Map;

/**
 * 이벤트에 대한 컨텍스트 메타데이터.
 * <p>
 * 이벤트가 발생한 상황(Context)을 설명하는 메타데이터의 집합입니다.
 * 누가(Actor), 어디서(Source), 어떤 흐름으로(Trace), 어떤 구조로(Schema) 발생했는지에 대한 정보를 포함합니다.
 *
 * @param source 이벤트 발생처 정보 (서비스, 환경 등)
 * @param actor  이벤트 유발자 정보 (사용자, 시스템 등)
 * @param trace  분산 추적 정보 (TraceId, SpanId 등)
 * @param schema 이벤트 스키마 정보 (이름, 버전 등)
 * @param tags   추가적인 사용자 정의 태그 (Key-Value)
 */
public record EventMetadata(
        EventSource source,
        EventActor actor,
        EventTrace trace,
        EventSchema schema,
        Map<String, String> tags
) {

    public EventMetadata {
        if (source == null) throw new IllegalArgumentException("source is required");
        if (actor == null) throw new IllegalArgumentException("actor is required");
        if (trace == null) throw new IllegalArgumentException("trace is required");
        if (schema == null) throw new IllegalArgumentException("schema is required");
        tags = (tags != null) ? Map.copyOf(tags) : Collections.emptyMap();
    }
}