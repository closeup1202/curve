package com.project.curve.spring.aop;

import com.project.curve.core.port.EventProducer;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.spring.annotation.Auditable;
import com.project.curve.spring.payload.AuditEventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Pointcut("@annotation(com.project.curve.spring.annotation.Auditable)")
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
        try {
            // 이벤트 타입 결정
            String eventType = determineEventType(joinPoint, auditable);

            // 페이로드 추출
            Object payloadData = extractPayload(joinPoint, auditable, returnValue);

            // AuditEventPayload로 래핑
            AuditEventPayload payload = createAuditPayload(eventType, joinPoint, payloadData);

            // 이벤트 발행
            EventSeverity severity = auditable.severity();
            eventProducer.publish(payload, severity);
            log.debug("Audit event published: eventType={}, severity={}", eventType, severity);

        } catch (Exception e) {
            log.error("Failed to publish audit event for method: {}", joinPoint.getSignature(), e);
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
