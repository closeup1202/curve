package com.project.curve.spring.audit.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.core.outbox.OutboxEvent;
import com.project.curve.core.outbox.OutboxEventRepository;
import com.project.curve.core.port.EventProducer;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.spring.audit.annotation.PublishEvent;
import com.project.curve.spring.exception.EventPublishException;
import com.project.curve.spring.audit.payload.EventPayload;
import com.project.curve.spring.metrics.CurveMetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PublishEventAspect {

    private final EventProducer eventProducer;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private CurveMetricsCollector metricsCollector;

    @Autowired(required = false)
    private OutboxEventRepository outboxEventRepository;

    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    @Pointcut("@annotation(com.project.curve.spring.audit.annotation.PublishEvent)")
    public void publishEventMethod() {
    }

    @Before("publishEventMethod() && @annotation(publishEvent)")
    public void beforeMethod(JoinPoint joinPoint, PublishEvent publishEvent) {
        if (publishEvent.phase() == PublishEvent.Phase.BEFORE) {
            publishEvent(joinPoint, publishEvent, null);
        }
    }

    @AfterReturning(pointcut = "publishEventMethod() && @annotation(publishEvent)", returning = "result")
    public void afterReturning(JoinPoint joinPoint, PublishEvent publishEvent, Object result) {
        if (publishEvent.phase() == PublishEvent.Phase.AFTER_RETURNING) {
            publishEvent(joinPoint, publishEvent, result);
        }
    }

    @After("publishEventMethod() && @annotation(publishEvent)")
    public void afterMethod(JoinPoint joinPoint, PublishEvent publishEvent) {
        if (publishEvent.phase() == PublishEvent.Phase.AFTER) {
            publishEvent(joinPoint, publishEvent, null);
        }
    }

    private void publishEvent(JoinPoint joinPoint, PublishEvent publishEvent, Object returnValue) {
        long startTime = System.currentTimeMillis();

        try {
            String eventType = determineEventType(joinPoint, publishEvent);
            Object payloadData = extractPayload(joinPoint, publishEvent, returnValue);
            EventPayload payload = createEventPayload(eventType, joinPoint, payloadData);

            // Outbox Pattern 사용 여부 확인
            if (publishEvent.outbox()) {
                saveToOutbox(joinPoint, publishEvent, payload, returnValue);
                log.debug("Event saved to outbox: eventType={}", eventType);
            } else {
                publishAndRecordSuccess(eventType, payload, publishEvent, startTime);
            }
        } catch (Exception e) {
            handlePublishFailure(joinPoint, publishEvent, startTime, e);
        }
    }

    private void publishAndRecordSuccess(String eventType, EventPayload payload, PublishEvent publishEvent, long startTime) {
        EventSeverity severity = publishEvent.severity();
        eventProducer.publish(payload, severity);
        log.debug("Event published: eventType={}, severity={}", eventType, severity);

        recordSuccessMetrics(eventType, startTime);
    }

    private void handlePublishFailure(JoinPoint joinPoint, PublishEvent publishEvent, long startTime, Exception e) {
        String eventType = determineEventTypeForFailure(joinPoint, publishEvent);

        log.error("Failed to publish event for method: {}, eventType={}, errorType={}",
                joinPoint.getSignature(), eventType, e.getClass().getSimpleName(), e);

        recordFailureMetrics(eventType, e, startTime);

        if (publishEvent.failOnError()) {
            throw new EventPublishException(
                    "Failed to publish event for method: " + joinPoint.getSignature(), e);
        } else {
            log.warn("Event publish failed but continuing execution (failOnError=false): " +
                    "eventType={}, method={}, errorType={}, errorMessage={}",
                    eventType, joinPoint.getSignature(), e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private String determineEventTypeForFailure(JoinPoint joinPoint, PublishEvent publishEvent) {
        try {
            return determineEventType(joinPoint, publishEvent);
        } catch (Exception ex) {
            return "unknown";
        }
    }

    private void recordSuccessMetrics(String eventType, long startTime) {
        if (metricsCollector != null) {
            long duration = System.currentTimeMillis() - startTime;
            metricsCollector.recordEventPublished(eventType, true, duration);
        }
    }

    private void recordFailureMetrics(String eventType, Exception e, long startTime) {
        if (metricsCollector != null) {
            long duration = System.currentTimeMillis() - startTime;
            metricsCollector.recordEventPublished(eventType, false, duration);
            metricsCollector.recordAuditFailure(eventType, e.getClass().getSimpleName());
        }
    }

    private String determineEventType(JoinPoint joinPoint, PublishEvent publishEvent) {
        if (!publishEvent.eventType().isBlank()) {
            return publishEvent.eventType();
        }
        // Default: 클래스명.메서드명
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        return className + "." + methodName;
    }

    private Object extractPayload(JoinPoint joinPoint, PublishEvent publishEvent, Object returnValue) {
        int payloadIndex = publishEvent.payloadIndex();

        if (payloadIndex == -1) {
            // 반환값 사용
            return returnValue;
        } else {
            // 파라미터 사용
            Object[] args = joinPoint.getArgs();
            if (payloadIndex >= 0 && payloadIndex < args.length) {
                return args[payloadIndex];
            } else {
                log.warn("Invalid payloadIndex: {}. Using null payload.", payloadIndex);
                return null;
            }
        }
    }

    private EventPayload createEventPayload(String eventType, JoinPoint joinPoint, Object payloadData) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = signature.getDeclaringType().getName();
        String methodName = method.getName();

        return new EventPayload(
                eventType,
                className,
                methodName,
                payloadData
        );
    }

    /**
     * Outbox 테이블에 이벤트 저장.
     * <p>
     * Transactional Outbox Pattern을 사용하여 DB 트랜잭션과 이벤트 발행의 원자성을 보장합니다.
     *
     * @param joinPoint     AOP 조인 포인트
     * @param publishEvent  @PublishEvent 어노테이션
     * @param payload       이벤트 페이로드
     * @param returnValue   메서드 반환값
     */
    private void saveToOutbox(
            JoinPoint joinPoint,
            PublishEvent publishEvent,
            EventPayload payload,
            Object returnValue
    ) {
        if (outboxEventRepository == null) {
            throw new EventPublishException(
                    "OutboxEventRepository is not configured. " +
                            "Please enable outbox configuration or set outbox=false."
            );
        }

        // Aggregate Type 검증
        String aggregateType = publishEvent.aggregateType();
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new EventPublishException(
                    "aggregateType must be specified when outbox=true. " +
                            "Example: @PublishEvent(outbox=true, aggregateType=\"Order\")"
            );
        }

        // Aggregate ID 추출
        String aggregateIdExpression = publishEvent.aggregateId();
        if (aggregateIdExpression == null || aggregateIdExpression.isBlank()) {
            throw new EventPublishException(
                    "aggregateId must be specified when outbox=true. " +
                            "Example: @PublishEvent(outbox=true, aggregateId=\"#result.orderId\")"
            );
        }

        String aggregateId = extractAggregateId(
                aggregateIdExpression,
                joinPoint,
                returnValue
        );

        // Payload를 JSON으로 직렬화
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new EventPublishException(
                    "Failed to serialize event payload to JSON: " + e.getMessage(),
                    e
            );
        }

        // OutboxEvent 생성 및 저장
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

    /**
     * SpEL 표현식으로 Aggregate ID 추출.
     *
     * @param expression  SpEL 표현식 (예: "#result.orderId", "#args[0]")
     * @param joinPoint   AOP 조인 포인트
     * @param returnValue 메서드 반환값
     * @return 추출된 Aggregate ID
     */
    private String extractAggregateId(
            String expression,
            JoinPoint joinPoint,
            Object returnValue
    ) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariable("result", returnValue);
            context.setVariable("args", joinPoint.getArgs());

            // 파라미터 이름으로 직접 접근 지원
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
        } catch (Exception e) {
            throw new EventPublishException(
                    "Failed to extract aggregateId using expression '" + expression + "': " + e.getMessage(),
                    e
            );
        }
    }
}
