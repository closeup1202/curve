package com.project.curve.spring.audit.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.core.outbox.OutboxEvent;
import com.project.curve.core.outbox.OutboxEventRepository;
import com.project.curve.spring.audit.annotation.PublishEvent;
import com.project.curve.spring.audit.payload.EventPayload;
import com.project.curve.spring.exception.EventPublishException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox 테이블에 이벤트를 저장하는 컴포넌트.
 * <p>
 * {@link PublishEventAspect}에서 outbox=true인 경우 호출됩니다.
 * SpEL 표현식을 통한 aggregateId 추출과 페이로드 직렬화를 담당합니다.
 *
 * @see PublishEventAspect
 * @see OutboxEventRepository
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxEventSaver {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    /**
     * Outbox 테이블에 이벤트를 저장합니다.
     *
     * @param joinPoint    AOP 조인 포인트
     * @param publishEvent @PublishEvent 어노테이션
     * @param payload      이벤트 페이로드
     * @param returnValue  메서드 반환값
     */
    public void save(JoinPoint joinPoint, PublishEvent publishEvent, EventPayload payload, Object returnValue) {
        String aggregateType = validateAggregateType(publishEvent);
        String aggregateId = validateAndExtractAggregateId(publishEvent, joinPoint, returnValue);
        String payloadJson = serializePayload(payload);

        String eventId = UUID.randomUUID().toString();
        OutboxEvent outboxEvent = new OutboxEvent(
                eventId,
                aggregateType,
                aggregateId,
                payload.eventTypeName(),
                payloadJson,
                Instant.now()
        );

        outboxEventRepository.save(outboxEvent);

        log.debug("Event saved to outbox: eventId={}, aggregateType={}, aggregateId={}, eventType={}",
                eventId, aggregateType, aggregateId, payload.eventTypeName());
    }

    private String validateAggregateType(PublishEvent publishEvent) {
        String aggregateType = publishEvent.aggregateType();
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new EventPublishException(
                    "aggregateType must be specified when outbox=true. " +
                            "Example: @PublishEvent(outbox=true, aggregateType=\"Order\")"
            );
        }
        return aggregateType;
    }

    private String validateAndExtractAggregateId(PublishEvent publishEvent, JoinPoint joinPoint, Object returnValue) {
        String expression = publishEvent.aggregateId();
        if (expression == null || expression.isBlank()) {
            throw new EventPublishException(
                    "aggregateId must be specified when outbox=true. " +
                            "Example: @PublishEvent(outbox=true, aggregateId=\"#result.orderId\")"
            );
        }
        return extractAggregateId(expression, joinPoint, returnValue);
    }

    private String serializePayload(EventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new EventPublishException(
                    "Failed to serialize event payload to JSON: " + e.getMessage(), e
            );
        }
    }

    /**
     * SpEL 표현식으로 Aggregate ID를 추출합니다.
     */
    private String extractAggregateId(String expression, JoinPoint joinPoint, Object returnValue) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariable("result", returnValue);
            context.setVariable("args", joinPoint.getArgs());

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] parameterNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }

            Expression expr = spelParser.parseExpression(expression);
            Object value = expr.getValue(context);

            if (value == null) {
                throw new EventPublishException(
                        "Failed to extract aggregateId: expression '" + expression + "' returned null"
                );
            }

            return value.toString();
        } catch (EventPublishException e) {
            throw e;
        } catch (Exception e) {
            throw new EventPublishException(
                    "Failed to extract aggregateId using expression '" + expression + "': " + e.getMessage(), e
            );
        }
    }
}
