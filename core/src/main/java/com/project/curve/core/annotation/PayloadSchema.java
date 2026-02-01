package com.project.curve.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이벤트 페이로드 클래스에 스키마 정보를 정의하기 위한 어노테이션.
 *
 * <p>사용 예시:</p>
 * <pre>
 * {@code
 * @PayloadSchema(name = "UserCreated", version = 2)
 * public record UserCreatedPayload(String userId, String email)
 *     implements DomainEventPayload { ... }
 * }
 * </pre>
 *
 * <p>이 어노테이션이 없으면 클래스 이름을 스키마 이름으로 사용하고 버전은 1로 설정됩니다.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PayloadSchema {

    /**
     * 스키마 이름. 비어있으면 클래스 이름을 사용합니다.
     */
    String name() default "";

    /**
     * 스키마 버전. 기본값은 1입니다.
     */
    int version() default 1;

    /**
     * 외부 Schema Registry 연동 시 사용할 스키마 ID.
     * 비어있으면 null로 처리됩니다.
     */
    String schemaId() default "";
}
