package com.project.curve.spring.context.actor;

import com.project.curve.core.context.ActorContextProvider;
import com.project.curve.core.envelope.EventActor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 기본 Actor Context Provider
 *
 * <p>Spring Security가 없는 환경에서 사용되는 기본 Provider입니다.
 * 모든 요청을 SYSTEM 사용자로 처리하며, 클라이언트 IP 정보를 수집합니다.</p>
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
public class DefaultActorContextProvider implements ActorContextProvider {

    private static final String DEFAULT_IP = "127.0.0.1";
    private static final String UNKNOWN_IP = "unknown";
    private static final String SYSTEM_USER = "SYSTEM";
    private static final String SYSTEM_ROLE = "ROLE_SYSTEM";

    @Override
    public EventActor getActor() {
        return new EventActor(SYSTEM_USER, SYSTEM_ROLE, getClientIp());
    }

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
