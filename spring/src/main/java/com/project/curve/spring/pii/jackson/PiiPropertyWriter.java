package com.project.curve.spring.pii.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.processor.PiiProcessorRegistry;
import com.project.curve.spring.pii.strategy.PiiStrategy;

/**
 * PII 필드를 처리하는 커스텀 PropertyWriter.
 * @PiiField 어노테이션이 붙은 필드의 값을 처리하여 직렬화한다.
 */
public class PiiPropertyWriter extends BeanPropertyWriter {

    private final BeanPropertyWriter delegate;
    private final PiiField piiField;
    private final PiiProcessorRegistry processorRegistry;

    public PiiPropertyWriter(BeanPropertyWriter delegate, PiiField piiField, PiiProcessorRegistry processorRegistry) {
        super(delegate);
        this.delegate = delegate;
        this.piiField = piiField;
        this.processorRegistry = processorRegistry;
    }

    @Override
    public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
        // EXCLUDE 전략이면 필드를 완전히 제외
        if (piiField.strategy() == PiiStrategy.EXCLUDE) {
            return;
        }

        Object value = delegate.get(bean);

        if (value == null) {
            if (_nullSerializer != null) {
                gen.writeFieldName(_name);
                _nullSerializer.serialize(null, gen, prov);
            } else if (!_suppressNulls) {
                gen.writeFieldName(_name);
                prov.defaultSerializeNull(gen);
            }
            return;
        }

        // 문자열 값만 PII 처리
        if (value instanceof String stringValue) {
            String processedValue = processorRegistry.process(stringValue, piiField);
            gen.writeFieldName(_name);
            gen.writeString(processedValue);
        } else {
            // 문자열이 아닌 경우 원본 그대로 직렬화
            delegate.serializeAsField(bean, gen, prov);
        }
    }
}
