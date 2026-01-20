package com.project.curve.spring.audit.aop;

import com.project.curve.core.port.EventProducer;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.spring.audit.annotation.Auditable;
import com.project.curve.spring.exception.AuditEventPublishException;
import com.project.curve.spring.audit.payload.AuditEventPayload;
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
public class AuditableAspect {

    private final EventProducer eventProducer;

    @Autowired(required = false)
    private CurveMetricsCollector metricsCollector;

    @Pointcut("@annotation(com.project.curve.spring.audit.annotation.Auditable)")
    public void auditableMethod() {
    }

    @Before("auditableMethod() && @annotation(auditable)")
    public void beforeMethod(JoinPoint joinPoint, Auditable auditable) {
        if (auditable.phase() == Auditable.Phase.BEFORE) {
            publishEvent(joinPoint, auditable, null);
        }
    }

    @AfterReturning(pointcut = "auditableMethod() && @annotation(auditable)", returning = "result")
    public void afterReturning(JoinPoint joinPoint, Auditable auditable, Object result) {
        if (auditable.phase() == Auditable.Phase.AFTER_RETURNING) {
            publishEvent(joinPoint, auditable, result);
        }
    }

    @After("auditableMethod() && @annotation(auditable)")
    public void afterMethod(JoinPoint joinPoint, Auditable auditable) {
        if (auditable.phase() == Auditable.Phase.AFTER) {
            publishEvent(joinPoint, auditable, null);
        }
    }

    private void publishEvent(JoinPoint joinPoint, Auditable auditable, Object returnValue) {
        long startTime = System.currentTimeMillis();

        try {
            String eventType = determineEventType(joinPoint, auditable);
            Object payloadData = extractPayload(joinPoint, auditable, returnValue);
            AuditEventPayload payload = createAuditPayload(eventType, joinPoint, payloadData);

            publishAndRecordSuccess(eventType, payload, auditable, startTime);

        } catch (Exception e) {
            handlePublishFailure(joinPoint, auditable, startTime, e);
        }
    }

    private void publishAndRecordSuccess(String eventType, AuditEventPayload payload, Auditable auditable, long startTime) {
        EventSeverity severity = auditable.severity();
        eventProducer.publish(payload, severity);
        log.debug("Audit event published: eventType={}, severity={}", eventType, severity);

        recordSuccessMetrics(eventType, startTime);
    }

    private void handlePublishFailure(JoinPoint joinPoint, Auditable auditable, long startTime, Exception e) {
        String eventType = determineEventTypeForFailure(joinPoint, auditable);

        log.error("Failed to publish audit event for method: {}, eventType={}, errorType={}",
                joinPoint.getSignature(), eventType, e.getClass().getSimpleName(), e);

        recordFailureMetrics(eventType, e, startTime);

        if (auditable.failOnError()) {
            throw new AuditEventPublishException(
                    "Failed to publish audit event for method: " + joinPoint.getSignature(), e);
        } else {
            log.warn("Audit event publish failed but continuing execution (failOnError=false): " +
                    "eventType={}, method={}, errorType={}, errorMessage={}",
                    eventType, joinPoint.getSignature(), e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private String determineEventTypeForFailure(JoinPoint joinPoint, Auditable auditable) {
        try {
            return determineEventType(joinPoint, auditable);
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

    private String determineEventType(JoinPoint joinPoint, Auditable auditable) {
        if (!auditable.eventType().isBlank()) {
            return auditable.eventType();
        }

        // 기본값: 클래스명.메서드명
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        return className + "." + methodName;
    }

    private Object extractPayload(JoinPoint joinPoint, Auditable auditable, Object returnValue) {
        int payloadIndex = auditable.payloadIndex();

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

    private AuditEventPayload createAuditPayload(String eventType, JoinPoint joinPoint, Object payloadData) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = signature.getDeclaringType().getName();
        String methodName = method.getName();

        return new AuditEventPayload(
                eventType,
                className,
                methodName,
                payloadData
        );
    }
}
