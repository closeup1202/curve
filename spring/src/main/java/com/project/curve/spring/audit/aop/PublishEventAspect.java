package com.project.curve.spring.audit.aop;

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
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PublishEventAspect {

    private final EventProducer eventProducer;

    @Autowired(required = false)
    private CurveMetricsCollector metricsCollector;

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

            publishAndRecordSuccess(eventType, payload, publishEvent, startTime);

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
}
