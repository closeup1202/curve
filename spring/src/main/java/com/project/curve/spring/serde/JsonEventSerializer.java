package com.project.curve.spring.serde;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.exception.EventSerializationException;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.serde.EventSerializer;

/**
 * Jackson ObjectMapper를 사용한 JSON 이벤트 직렬화 구현체.
 * <p>
 * {@link ObjectMapper}에 등록된 모듈(PiiModule 등)이 자동으로 적용되어
 * PII 마스킹, 날짜 포맷 변환 등이 투명하게 처리됩니다.
 *
 * <h3>주요 특징</h3>
 * <ul>
 *   <li>ObjectMapper 설정 기반 직렬화 (PII, 날짜 포맷 등)</li>
 *   <li>JsonProcessingException → EventSerializationException 변환</li>
 *   <li>Thread-safe (ObjectMapper는 thread-safe)</li>
 * </ul>
 *
 * @see com.project.curve.spring.pii.jackson.PiiModule
 * @see ObjectMapper
 * @see EventSerializer
 */
public class JsonEventSerializer implements EventSerializer {

    private final ObjectMapper objectMapper;

    public JsonEventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public <T extends DomainEventPayload> String serialize(EventEnvelope<T> envelope) throws EventSerializationException {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException(
                    "Failed to serialize EventEnvelope. eventId=" + envelope.eventId().value(),
                    e
            );
        }
    }
}
