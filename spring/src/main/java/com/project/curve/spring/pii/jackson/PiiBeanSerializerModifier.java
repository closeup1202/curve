package com.project.curve.spring.pii.jackson;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.processor.PiiProcessorRegistry;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Bean 직렬화 시 @PiiField 어노테이션이 있는 필드를 감지하여
 * PiiPropertyWriter로 대체하는 Modifier.
 */
@RequiredArgsConstructor
public class PiiBeanSerializerModifier extends BeanSerializerModifier {

    private final PiiProcessorRegistry processorRegistry;

    @Override
    public List<BeanPropertyWriter> changeProperties(
            SerializationConfig config,
            BeanDescription beanDesc,
            List<BeanPropertyWriter> beanProperties) {

        List<BeanPropertyWriter> result = new ArrayList<>();

        for (BeanPropertyWriter writer : beanProperties) {
            PiiField piiField = findPiiFieldAnnotation(beanDesc.getBeanClass(), writer.getName());

            if (piiField != null) {
                result.add(new PiiPropertyWriter(writer, piiField, processorRegistry));
            } else {
                result.add(writer);
            }
        }

        return result;
    }

    private PiiField findPiiFieldAnnotation(Class<?> clazz, String fieldName) {
        // 현재 클래스와 상위 클래스에서 필드 탐색
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            try {
                Field field = currentClass.getDeclaredField(fieldName);
                PiiField annotation = field.getAnnotation(PiiField.class);
                if (annotation != null) {
                    return annotation;
                }
            } catch (NoSuchFieldException e) {
                // 현재 클래스에 없으면 상위 클래스 탐색
            }
            currentClass = currentClass.getSuperclass();
        }

        // Record 컴포넌트에서도 탐색
        assert clazz != null;
        if (clazz.isRecord()) {
            try {
                for (var component : clazz.getRecordComponents()) {
                    if (component.getName().equals(fieldName)) {
                        return component.getAnnotation(PiiField.class);
                    }
                }
            } catch (Exception e) {
                // 무시
            }
        }

        return null;
    }
}
