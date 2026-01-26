package com.project.curve.spring.audit.validation;

import com.project.curve.spring.audit.annotation.PublishEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

/**
 * 애플리케이션 시작 시 @PublishEvent의 SpEL 표현식 유효성을 검사하는 Validator.
 * <p>
 * 잘못된 SpEL 표현식이 있는 경우 경고 로그를 출력하여 개발자가 인지할 수 있도록 합니다.
 */
@Slf4j
@Component
public class SpelExpressionValidator implements ApplicationListener<ContextRefreshedEvent>, ApplicationContextAware {

    private ApplicationContext applicationContext;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        validateSpelExpressions();
    }

    private void validateSpelExpressions() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                Class<?> targetClass = AopUtils.getTargetClass(bean);
                
                // Spring 내부 빈이나 프록시 제외 필터링을 할 수도 있지만, 
                // ReflectionUtils.doWithMethods가 안전하게 처리하므로 모든 빈 검사
                ReflectionUtils.doWithMethods(targetClass, method -> validateMethod(targetClass, method));
            } catch (Exception e) {
                // 특정 빈 로드 실패 시 무시하고 계속 진행
                log.trace("Failed to validate bean '{}': {}", beanName, e.getMessage());
            }
        }
    }

    private void validateMethod(Class<?> targetClass, Method method) {
        PublishEvent publishEvent = AnnotationUtils.findAnnotation(method, PublishEvent.class);
        if (publishEvent == null) {
            return;
        }

        validatePayloadExpression(targetClass, method, publishEvent);
        validateAggregateIdExpression(targetClass, method, publishEvent);
    }

    private void validatePayloadExpression(Class<?> targetClass, Method method, PublishEvent publishEvent) {
        String expression = publishEvent.payload();
        if (expression != null && !expression.isBlank()) {
            try {
                spelParser.parseExpression(expression);
            } catch (ParseException e) {
                log.error("Invalid SpEL expression in @PublishEvent(payload=\"{}\") on method {}.{}: {}",
                        expression, targetClass.getSimpleName(), method.getName(), e.getMessage());
            }
        }
    }

    private void validateAggregateIdExpression(Class<?> targetClass, Method method, PublishEvent publishEvent) {
        String expression = publishEvent.aggregateId();
        if (publishEvent.outbox() && expression != null && !expression.isBlank()) {
            try {
                spelParser.parseExpression(expression);
            } catch (ParseException e) {
                log.error("Invalid SpEL expression in @PublishEvent(aggregateId=\"{}\") on method {}.{}: {}",
                        expression, targetClass.getSimpleName(), method.getName(), e.getMessage());
            }
        }
    }
}
