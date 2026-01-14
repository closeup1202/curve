package com.project.curve.spring.context;

import com.project.curve.core.context.ActorContextProvider;
import com.project.curve.core.envelope.EventActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class SpringSecurityActorProvider implements ActorContextProvider {

    private static final String DEFAULT_IP = "127.0.0.1";

    @Override
    public EventActor getActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            return new EventActor("SYSTEM", "ROLE_SYSTEM", getClientIp());
        }

        String userId = auth.getName();

        // 권한 정보 (첫 번째 권한 추출 예시)
        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(Object::toString)
                .orElse("ROLE_USER");

        return new EventActor(userId, role, getClientIp());
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes == null) {
                return DEFAULT_IP;
            }

            HttpServletRequest request = attributes.getRequest();

            // X-Forwarded-For 헤더 확인 (프록시를 거친 경우)
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }

            // X-Real-IP 헤더 확인 (nginx 등에서 사용)
            ip = request.getHeader("X-Real-IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            // Proxy-Client-IP 헤더 확인
            ip = request.getHeader("Proxy-Client-IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            // WL-Proxy-Client-IP 헤더 확인 (WebLogic)
            ip = request.getHeader("WL-Proxy-Client-IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            // 직접 연결된 경우
            ip = request.getRemoteAddr();
            return ip != null ? ip : DEFAULT_IP;

        } catch (Exception e) {
            return DEFAULT_IP;
        }
    }
}
