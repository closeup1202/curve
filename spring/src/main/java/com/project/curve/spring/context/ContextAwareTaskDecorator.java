package com.project.curve.spring.context;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;

/**
 * 비동기 작업 실행 시 부모 스레드의 컨텍스트를 자식 스레드로 전파하는 TaskDecorator.
 * <p>
 * - RequestContextHolder: HTTP 요청 관련 정보(Request, Session 등) 전파
 * - MDC (Mapped Diagnostic Context): 로깅 추적 ID 등 전파
 * <p>
 * @EnableAsync와 함께 사용하여 @Async 메서드 호출 시 컨텍스트 유실을 방지합니다.
 */
public class ContextAwareTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();

        return () -> {
            try {
                RequestContextHolder.setRequestAttributes(requestAttributes);
                MDC.setContextMap(mdcContext);
                runnable.run();
            } finally {
                MDC.clear();
                RequestContextHolder.resetRequestAttributes();
            }
        };
    }
}
