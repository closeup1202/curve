package com.project.curve.spring.context.actor;

import com.project.curve.core.context.ActorContextProvider;
import com.project.curve.core.envelope.EventActor;
import com.project.curve.spring.util.ClientIpExtractor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Spring Security 기반 Actor Context Provider
 * <p>
 * Spring Security의 인증 정보를 사용하여 EventActor를 생성합니다.
 * 인증되지 않은 요청은 SYSTEM 사용자로 처리됩니다.
 * <p>
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
 * @see ClientIpExtractor
 * @see <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.webserver.use-behind-a-proxy-server">Spring Boot Proxy Configuration</a>
 */
public class SpringSecurityActorContextProvider implements ActorContextProvider {

    @Override
    public EventActor getActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            return new EventActor("SYSTEM", "ROLE_SYSTEM", ClientIpExtractor.getClientIp());
        }

        String userId = auth.getName();

        // 권한 정보 (첫 번째 권한 추출)
        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(Object::toString)
                .orElse("ROLE_USER");

        return new EventActor(userId, role, ClientIpExtractor.getClientIp());
    }
}
