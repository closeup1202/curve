package com.project.curve.spring.annotation;

import com.project.curve.core.type.EventSeverity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 메서드 실행 시 자동으로 이벤트를 발행하는 어노테이션
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * 이벤트 타입 이름
     * 기본값: 메서드 이름을 기반으로 생성
     */
    String eventType() default "";

    /**
     * 이벤트 심각도
     */
    EventSeverity severity() default EventSeverity.INFO;

    /**
     * 이벤트 페이로드로 사용할 파라미터 인덱스
     * -1: 반환값 사용 (기본값)
     * 0 이상: 해당 인덱스의 파라미터 사용
     */
    int payloadIndex() default -1;

    /**
     * 이벤트 발행 시점
     */
    Phase phase() default Phase.AFTER_RETURNING;

    /**
     * 이벤트 발행 시점
     */
    enum Phase {
        // 메서드 실행 전
        BEFORE,

        // 메서드 정상 반환 후
        AFTER_RETURNING,

        // 메서드 실행 후 (예외 발생 여부 무관)
        AFTER
    }
}
