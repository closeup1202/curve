package com.project.curve.spring.pii.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.project.curve.spring.pii.processor.PiiProcessorRegistry;

/**
 * PII 처리를 위한 Jackson 모듈.
 * ObjectMapper에 등록하면 @PiiField 어노테이션이 자동으로 처리된다.
 *
 * <pre>{@code
 * ObjectMapper mapper = new ObjectMapper();
 * mapper.registerModule(new PiiModule(processorRegistry));
 *
 * // 직렬화 시 자동 마스킹
 * String json = mapper.writeValueAsString(userPayload);
 * // {"email":"j***@gm***.com","phone":"010-****-5678",...}
 * }</pre>
 */
public class PiiModule extends SimpleModule {

    private static final String MODULE_NAME = "PiiModule";

    public PiiModule(PiiProcessorRegistry processorRegistry) {
        super(MODULE_NAME);
        setSerializerModifier(new PiiBeanSerializerModifier(processorRegistry));
    }
}
