package com.project.curve.core.schema;

/**
 * 스키마 버전 간 마이그레이션을 지원하는 인터페이스.
 * <p>
 * 이벤트 페이로드가 버전 업그레이드될 때 데이터를 변환하는 로직을 제공합니다.
 * <p>
 * <b>예제:</b>
 * <pre>{@code
 * public class OrderCreatedPayloadV1ToV2Migration implements SchemaMigration<OrderCreatedPayloadV1, OrderCreatedPayloadV2> {
 *     @Override
 *     public OrderCreatedPayloadV2 migrate(OrderCreatedPayloadV1 source) {
 *         return new OrderCreatedPayloadV2(
 *             source.orderId(),
 *             source.customerId(),
 *             source.productName(),
 *             source.quantity(),
 *             source.totalAmount(),
 *             "PENDING"  // 새로운 필드 기본값
 *         );
 *     }
 *
 *     @Override
 *     public SchemaVersion fromVersion() {
 *         return new SchemaVersion("OrderCreated", 1, OrderCreatedPayloadV1.class);
 *     }
 *
 *     @Override
 *     public SchemaVersion toVersion() {
 *         return new SchemaVersion("OrderCreated", 2, OrderCreatedPayloadV2.class);
 *     }
 * }
 * }</pre>
 *
 * @param <FROM> 소스 페이로드 타입 (구버전)
 * @param <TO>   타겟 페이로드 타입 (신버전)
 */
public interface SchemaMigration<FROM, TO> {

    /**
     * 구버전 페이로드를 신버전으로 변환합니다.
     *
     * @param source 구버전 페이로드
     * @return 신버전 페이로드
     */
    TO migrate(FROM source);

    /**
     * 마이그레이션 시작 버전을 반환합니다.
     *
     * @return 시작 버전
     */
    SchemaVersion fromVersion();

    /**
     * 마이그레이션 대상 버전을 반환합니다.
     *
     * @return 대상 버전
     */
    SchemaVersion toVersion();

    /**
     * 마이그레이션이 적용 가능한지 확인합니다.
     *
     * @param from 소스 버전
     * @param to   타겟 버전
     * @return 적용 가능하면 true
     */
    default boolean isApplicable(SchemaVersion from, SchemaVersion to) {
        return fromVersion().equals(from) && toVersion().equals(to);
    }
}
