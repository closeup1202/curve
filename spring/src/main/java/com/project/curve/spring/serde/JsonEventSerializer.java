package com.project.curve.spring.serde;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.exception.EventSerializationException;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.serde.EventSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * Jackson ObjectMapper를 사용한 JSON 이벤트 직렬화 구현체.
 * <p>
 * {@link ObjectMapper}에 등록된 모듈(예: PiiModule)이 자동으로 적용되어,
 * PII 마스킹, 날짜 포맷 변환 등을 투명하게 처리합니다.
 *
 * <h3>주요 특징</h3>
 * <ul>
 *   <li>ObjectMapper 설정 기반 직렬화 (PII, 날짜 포맷 등)</li>
 *   <li>JsonProcessingException → EventSerializationException 변환</li>
 *   <li>스레드 안전 (ObjectMapper는 스레드 안전함)</li>
 * </ul>
 *
 * @see com.project.curve.spring.pii.jackson.PiiModule
 * @see ObjectMapper
 * @see EventSerializer
 */
@RequiredArgsConstructor
@Component
public class JsonEventSerializer implements EventSerializer {

    private final ObjectMapper objectMapper;

    @Override
    public <T extends DomainEventPayload> String serialize(EventEnvelope<T> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException(
                    "Failed to serialize EventEnvelope. eventId=" + envelope.eventId().value(), e
            );
        }
    }
}
