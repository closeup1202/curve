package com.project.curve.autoconfigure.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.autoconfigure.CurveProperties;
import com.project.curve.core.serde.EventSerializer;
import com.project.curve.spring.serde.JsonEventSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class CurveEventSerializerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EventSerializer.class)
    public EventSerializer eventSerializer(ObjectMapper objectMapper, CurveProperties properties) {
        CurveProperties.Serde.SerdeType type = properties.getSerde().getType();

        if (type == CurveProperties.Serde.SerdeType.JSON) {
            log.info("Using JSON EventSerializer");
            return new JsonEventSerializer(objectMapper);
        } else {
            // 다른 타입은 아직 구현되지 않았으므로 경고 후 JSON 사용 또는 예외 발생
            // 여기서는 확장성을 위해 구조만 잡아두고 JSON으로 폴백하거나 예외를 던질 수 있음
            // 현재는 구현체가 없으므로 예외를 던져 명시적으로 알림
            throw new UnsupportedOperationException(
                    "Serializer type " + type + " is not yet implemented. Please use JSON or provide a custom EventSerializer bean."
            );
        }
    }
}
