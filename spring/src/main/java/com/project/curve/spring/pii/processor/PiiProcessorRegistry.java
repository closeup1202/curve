package com.project.curve.spring.pii.processor;

import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.strategy.PiiStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PiiProcessor 레지스트리.
 * 전략에 맞는 프로세서를 찾아 처리를 위임합니다.
 */
@Component
public class PiiProcessorRegistry {

    private final Map<PiiStrategy, PiiProcessor> processorMap;

    public PiiProcessorRegistry(List<PiiProcessor> processors) {
        this.processorMap = processors.stream()
                .collect(Collectors.toMap(
                        PiiProcessor::supportedStrategy,
                        Function.identity(),
                        (existing, replacement) -> replacement
                ));
    }

    /**
     * PiiField 어노테이션에 따라 값을 처리합니다.
     *
     * @param value 원본 값
     * @param piiField PiiField 어노테이션
     * @return 처리된 값, EXCLUDE 전략인 경우 null
     */
    public String process(String value, PiiField piiField) {
        if (piiField.strategy() == PiiStrategy.EXCLUDE) {
            return null; // 필드 제외는 Jackson에서 처리됨
        }

        PiiProcessor processor = processorMap.get(piiField.strategy());
        if (processor == null) {
            // 프로세서를 찾을 수 없는 경우 원본 반환 (안전한 폴백)
            return value;
        }

        return processor.process(value, piiField);
    }

    /**
     * 지정된 전략에 대한 프로세서가 등록되어 있는지 확인합니다.
     */
    public boolean hasProcessor(PiiStrategy strategy) {
        return processorMap.containsKey(strategy);
    }
}
