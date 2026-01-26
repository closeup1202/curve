package com.project.curve.core.serde;

import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.exception.EventSerializationException;
import com.project.curve.core.payload.DomainEventPayload;

/**
 * 이벤트 Envelope를 직렬화하는 인터페이스.
 * <p>
 * PII(개인식별정보) 처리, 압축, 암호화 등 다양한 직렬화 전략을 구현할 수 있습니다.
 *
 * <h3>구현 예시</h3>
 * <ul>
 *   <li>JsonEventSerializer: JSON 직렬화 (기본)</li>
 *   <li>PiiMaskingEventSerializer: PII 필드 마스킹 후 직렬화</li>
 *   <li>CompressedEventSerializer: 압축된 직렬화</li>
 *   <li>AvroEventSerializer: Avro 바이너리 직렬화</li>
 * </ul>
 *
 * @see EventEnvelope
 * @see EventSerializationException
 */
public interface EventSerializer {

    /**
     * EventEnvelope를 직렬화합니다.
     *
     * @param envelope 직렬화할 이벤트 Envelope
     * @param <T>      이벤트 페이로드 타입
     * @return 직렬화된 객체 (String, byte[], GenericRecord 등)
     * @throws EventSerializationException 직렬화 실패 시
     */
    <T extends DomainEventPayload> Object serialize(EventEnvelope<T> envelope) throws EventSerializationException;
}
