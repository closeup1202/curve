package com.project.curve.spring.context.actor;

import com.project.curve.core.context.ActorContextProvider;
import com.project.curve.core.envelope.EventActor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Spring Security 기반 Actor Context Provider
 *
 * <h3>클라이언트 IP 처리 방식</h3>
 * <p>Spring Boot의 ForwardedHeaderFilter를 사용하면 request.getRemoteAddr()가
 * 자동으로 X-Forwarded-For 헤더를 처리하여 올바른 클라이언트 IP를 반환합니다.</p>
 *
 * <h3>보안 설정 (권장)</h3>
 * <pre>
 * # application.yml
 * server:
 *   forward-headers-strategy: framework  # Spring Boot의 ForwardedHeaderFilter 활성화
 *   tomcat:
 *     remoteip:
 *       internal-proxies: 10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|192\\.168\\.\\d{1,3}\\.\\d{1,3}
 *       protocol-header: X-Forwarded-Proto
 *       remote-ip-header: X-Forwarded-For
 * </pre>
 *
 * @see <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.webserver.use-behind-a-proxy-server">Spring Boot Proxy Configuration</a>
 */
@Slf4j
public class SpringSecurityActorContextProvider implements ActorContextProvider {

    private static final String DEFAULT_IP = "127.0.0.1";
    private static final String UNKNOWN_IP = "unknown";

    @Override
    public EventActor getActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            return new EventActor("SYSTEM", "ROLE_SYSTEM", getClientIp());
        }

        String userId = auth.getName();

        // 권한 정보 (첫 번째 권한 추출)
        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(Object::toString)
                .orElse("ROLE_USER");

        return new EventActor(userId, role, getClientIp());
    }

    /**
     * 클라이언트 IP 주소 추출
     *
     * <p>Spring Boot의 ForwardedHeaderFilter가 활성화된 경우,
     * request.getRemoteAddr()가 X-Forwarded-For 헤더를 자동으로 처리합니다.</p>
     *
     * <p>보안을 위해 헤더를 직접 읽지 않고, Spring이 검증한 remoteAddr만 사용합니다.</p>
     *
     * @return 클라이언트 IP 주소
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes == null) {
                log.debug("No request context available, using default IP");
                return DEFAULT_IP;
            }

            HttpServletRequest request = attributes.getRequest();
            String remoteAddr = request.getRemoteAddr();

            // Spring의 ForwardedHeaderFilter가 처리한 IP 사용
            if (remoteAddr != null && !remoteAddr.isEmpty() && !UNKNOWN_IP.equalsIgnoreCase(remoteAddr)) {
                return remoteAddr;
            }

            log.warn("Remote address is null or unknown, using default IP");
            return DEFAULT_IP;

        } catch (Exception e) {
            log.error("Failed to extract client IP, using default IP", e);
            return DEFAULT_IP;
        }
    }
}
