package com.project.curve.spring.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 클라이언트 IP 주소 추출 유틸리티.
 * <p>
 * Spring Boot의 ForwardedHeaderFilter를 사용하여 프록시 환경에서도
 * 올바른 클라이언트 IP를 추출합니다.
 * <p>
 * <b>클라이언트 IP 처리 방식:</b>
 * <ul>
 *   <li>Spring Boot의 ForwardedHeaderFilter가 활성화된 경우,
 *       request.getRemoteAddr()가 X-Forwarded-For 헤더를 자동으로 처리</li>
 *   <li>보안을 위해 헤더를 직접 읽지 않고, Spring이 검증한 remoteAddr만 사용</li>
 *   <li>요청 컨텍스트가 없거나 오류 발생 시 기본 IP(127.0.0.1) 반환</li>
 * </ul>
 * <p>
 * <b>보안 설정 (권장):</b>
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
public final class ClientIpExtractor {

    private static final String DEFAULT_IP = "127.0.0.1";
    private static final String UNKNOWN_IP = "unknown";

    private ClientIpExtractor() {
        // Utility class - prevent instantiation
    }

    public static String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes == null) {
                log.debug("No request context available, using default IP");
                return DEFAULT_IP;
            }

            HttpServletRequest request = attributes.getRequest();
            String remoteAddr = request.getRemoteAddr();

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

    /**
     * 지정된 HttpServletRequest에서 클라이언트 IP 주소를 추출합니다.
     * <p>
     * 테스트나 특수한 경우에 직접 request 객체를 제공할 때 사용합니다.
     *
     * @param request HTTP 요청
     * @return 클라이언트 IP 주소 (추출 실패 시 "127.0.0.1")
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            log.debug("Request is null, using default IP");
            return DEFAULT_IP;
        }

        try {
            String remoteAddr = request.getRemoteAddr();

            if (remoteAddr != null && !remoteAddr.isEmpty() && !UNKNOWN_IP.equalsIgnoreCase(remoteAddr)) {
                return remoteAddr;
            }

            log.warn("Remote address is null or unknown, using default IP");
            return DEFAULT_IP;

        } catch (Exception e) {
            log.error("Failed to extract client IP from request, using default IP", e);
            return DEFAULT_IP;
        }
    }

    public static String getDefaultIp() {
        return DEFAULT_IP;
    }
}
