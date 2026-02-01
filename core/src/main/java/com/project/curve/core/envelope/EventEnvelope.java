package com.project.curve.core.envelope;

import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.core.type.EventType;
import lombok.NonNull;

import java.time.Instant;

/**
 * 도메인 이벤트를 위한 표준 엔벨로프(Envelope).
 * <p>
 * EventEnvelope는 비즈니스 이벤트 페이로드를 풍부한 컨텍스트 메타데이터로 감싸서,
 * 분산 시스템에서의 포괄적인 이벤트 추적, 감사(Auditing), 처리를 가능하게 합니다.
 * Curve 라이브러리의 모든 이벤트는 이 표준 구조를 따릅니다.
 * </p>
 *
 * <h3>이벤트 구조:</h3>
 * <pre>
 * EventEnvelope
 * ├── eventId          고유 이벤트 식별자 (Snowflake ID)
 * ├── eventType        이벤트 타입/이름
 * ├── severity         이벤트 중요도 (INFO, WARN, ERROR, CRITICAL)
 * ├── metadata         컨텍스트 메타데이터
 * │   ├── source       이벤트 발생처 (서비스, 환경, 버전)
 * │   ├── actor        이벤트 유발자 (사용자, 역할, IP)
 * │   ├── trace        분산 추적 정보 (traceId, spanId, correlationId)
 * │   ├── schema       이벤트 스키마 정보
 * │   └── tags         사용자 정의 메타데이터 태그
 * ├── payload          비즈니스 이벤트 데이터
 * ├── occurredAt       이벤트 발생 시각
 * └── publishedAt      이벤트 발행 시각
 * </pre>
 *
 * <h3>주요 특징:</h3>
 * <ul>
 *   <li><b>불변성(Immutable)</b> - Java Record를 사용하여 불변성 보장</li>
 *   <li><b>타입 안전성(Type-safe)</b> - 제네릭 페이로드 타입 파라미터 사용</li>
 *   <li><b>풍부한 메타데이터</b> - 포괄적인 컨텍스트 정보 제공</li>
 *   <li><b>추적성(Traceability)</b> - 분산 추적 및 이벤트 체인 지원</li>
 *   <li><b>Null 안전성(Null-safe)</b> - 모든 필드는 null이 아님을 보장</li>
 * </ul>
 *
 * <h3>사용 예시:</h3>
 * <pre>{@code
 * EventEnvelope<OrderCreatedPayload> envelope = EventEnvelope.of(
 *     EventId.of("1234567890"),
 *     new OrderCreatedEventType(),
 *     EventSeverity.INFO,
 *     metadata,
 *     new OrderCreatedPayload(order),
 *     Instant.now(),
 *     Instant.now()
 * );
 * }</pre>
 *
 * @param <T> 이벤트 페이로드 타입 ({@link DomainEventPayload} 상속 필수)
 * @param eventId 고유 이벤트 식별자
 * @param eventType 이벤트의 타입/이름
 * @param severity 이벤트의 중요도 레벨
 * @param metadata 컨텍스트 메타데이터 (source, actor, trace 등)
 * @param payload 비즈니스 이벤트 데이터
 * @param occurredAt 이벤트가 발생한 시각
 * @param publishedAt 이벤트가 발행된 시각
 * @see EventMetadata
 * @see DomainEventPayload
 * @see EventSeverity
 * @since 0.0.1
 */
public record EventEnvelope<T extends DomainEventPayload>(
        @NonNull EventId eventId,
        @NonNull EventType eventType,
        @NonNull EventSeverity severity,
        @NonNull EventMetadata metadata,
        @NonNull T payload,
        @NonNull Instant occurredAt,
        @NonNull Instant publishedAt
) {

    public static <T extends DomainEventPayload> EventEnvelope<T> of(
            EventId eventId,
            EventType eventType,
            EventSeverity severity,
            EventMetadata metadata,
            T payload,
            Instant occurredAt,
            Instant publishedAt
    ) {
        return new EventEnvelope<>(
                eventId,
                eventType,
                severity,
                metadata,
                payload,
                occurredAt,
                publishedAt
        );
    }
}
