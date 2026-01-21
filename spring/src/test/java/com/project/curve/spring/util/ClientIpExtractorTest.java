package com.project.curve.spring.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClientIpExtractor 테스트")
class ClientIpExtractorTest {

    @AfterEach
    void tearDown() {
        // 각 테스트 후 RequestContext 정리
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    @DisplayName("getClientIp() - RequestContext 기반")
    class GetClientIpFromContextTest {

        @Test
        @DisplayName("정상적인 클라이언트 IP를 추출할 수 있다")
        void extractValidClientIp() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.168.1.100");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("192.168.1.100");
        }

        @Test
        @DisplayName("IPv6 주소를 정상적으로 추출할 수 있다")
        void extractIpv6Address() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
        }

        @Test
        @DisplayName("RequestContext가 없으면 기본 IP를 반환한다")
        void noRequestContext_shouldReturnDefaultIp() {
            // Given
            RequestContextHolder.resetRequestAttributes();

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("remoteAddr이 null이면 기본 IP를 반환한다")
        void nullRemoteAddr_shouldReturnDefaultIp() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr(null);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("remoteAddr이 빈 문자열이면 기본 IP를 반환한다")
        void emptyRemoteAddr_shouldReturnDefaultIp() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("remoteAddr이 'unknown'이면 기본 IP를 반환한다")
        void unknownRemoteAddr_shouldReturnDefaultIp() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("unknown");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("remoteAddr이 'UNKNOWN'(대소문자 무관)이면 기본 IP를 반환한다")
        void unknownUppercaseRemoteAddr_shouldReturnDefaultIp() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("UNKNOWN");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }
    }

    @Nested
    @DisplayName("getClientIp(HttpServletRequest) - 직접 request 제공")
    class GetClientIpFromRequestTest {

        @Test
        @DisplayName("정상적인 클라이언트 IP를 추출할 수 있다")
        void extractValidClientIp() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.5");

            // When
            String clientIp = ClientIpExtractor.getClientIp(request);

            // Then
            assertThat(clientIp).isEqualTo("10.0.0.5");
        }

        @Test
        @DisplayName("null request는 기본 IP를 반환한다")
        void nullRequest_shouldReturnDefaultIp() {
            // When
            String clientIp = ClientIpExtractor.getClientIp(null);

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("remoteAddr이 null이면 기본 IP를 반환한다")
        void nullRemoteAddr_shouldReturnDefaultIp() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr(null);

            // When
            String clientIp = ClientIpExtractor.getClientIp(request);

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("remoteAddr이 빈 문자열이면 기본 IP를 반환한다")
        void emptyRemoteAddr_shouldReturnDefaultIp() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("");

            // When
            String clientIp = ClientIpExtractor.getClientIp(request);

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("remoteAddr이 'unknown'이면 기본 IP를 반환한다")
        void unknownRemoteAddr_shouldReturnDefaultIp() {
            // Given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("unknown");

            // When
            String clientIp = ClientIpExtractor.getClientIp(request);

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }
    }

    @Nested
    @DisplayName("getDefaultIp() 테스트")
    class GetDefaultIpTest {

        @Test
        @DisplayName("기본 IP는 127.0.0.1이다")
        void getDefaultIp() {
            // When
            String defaultIp = ClientIpExtractor.getDefaultIp();

            // Then
            assertThat(defaultIp).isEqualTo("127.0.0.1");
        }
    }

    @Nested
    @DisplayName("실제 프록시 환경 시뮬레이션")
    class ProxyEnvironmentTest {

        @Test
        @DisplayName("ForwardedHeaderFilter 처리 후 remoteAddr는 실제 클라이언트 IP를 가진다")
        void forwardedHeaderFilterProcessed() {
            // Given: ForwardedHeaderFilter가 X-Forwarded-For를 처리한 후
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("203.0.113.42");  // 실제 클라이언트 IP (ForwardedHeaderFilter 처리 후)

            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("203.0.113.42");
        }

        @Test
        @DisplayName("로드밸런서 뒤에서도 정상적으로 작동한다")
        void behindLoadBalancer() {
            // Given: AWS ALB, Nginx 등 로드밸런서 환경
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("172.16.0.10");  // 로드밸런서가 ForwardedHeaderFilter 처리 후

            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("172.16.0.10");
        }
    }

    @Nested
    @DisplayName("예외 처리 테스트")
    class ExceptionHandlingTest {

        @Test
        @DisplayName("예외 발생 시 기본 IP를 반환한다")
        void exceptionDuringExtraction_shouldReturnDefaultIp() {
            // Given: request가 예외를 발생시키도록 구성
            HttpServletRequest faultyRequest = new HttpServletRequest() {
                @Override
                public String getRemoteAddr() {
                    throw new RuntimeException("Simulated error");
                }

                // 나머지 메서드는 기본 구현 (미사용)
                @Override
                public Object getAttribute(String name) {
                    return null;
                }

                @Override
                public java.util.Enumeration<String> getAttributeNames() {
                    return null;
                }

                @Override
                public String getCharacterEncoding() {
                    return null;
                }

                @Override
                public void setCharacterEncoding(String env) {
                }

                @Override
                public int getContentLength() {
                    return 0;
                }

                @Override
                public long getContentLengthLong() {
                    return 0;
                }

                @Override
                public String getContentType() {
                    return null;
                }

                @Override
                public jakarta.servlet.ServletInputStream getInputStream() {
                    return null;
                }

                @Override
                public String getParameter(String name) {
                    return null;
                }

                @Override
                public java.util.Enumeration<String> getParameterNames() {
                    return null;
                }

                @Override
                public String[] getParameterValues(String name) {
                    return null;
                }

                @Override
                public java.util.Map<String, String[]> getParameterMap() {
                    return null;
                }

                @Override
                public String getProtocol() {
                    return null;
                }

                @Override
                public String getScheme() {
                    return null;
                }

                @Override
                public String getServerName() {
                    return null;
                }

                @Override
                public int getServerPort() {
                    return 0;
                }

                @Override
                public java.io.BufferedReader getReader() {
                    return null;
                }

                @Override
                public String getRemoteHost() {
                    return null;
                }

                @Override
                public void setAttribute(String name, Object o) {
                }

                @Override
                public void removeAttribute(String name) {
                }

                @Override
                public java.util.Locale getLocale() {
                    return null;
                }

                @Override
                public java.util.Enumeration<java.util.Locale> getLocales() {
                    return null;
                }

                @Override
                public boolean isSecure() {
                    return false;
                }

                @Override
                public jakarta.servlet.RequestDispatcher getRequestDispatcher(String path) {
                    return null;
                }

                @Override
                public int getRemotePort() {
                    return 0;
                }

                @Override
                public String getLocalName() {
                    return null;
                }

                @Override
                public String getLocalAddr() {
                    return null;
                }

                @Override
                public int getLocalPort() {
                    return 0;
                }

                @Override
                public jakarta.servlet.ServletContext getServletContext() {
                    return null;
                }

                @Override
                public jakarta.servlet.AsyncContext startAsync() {
                    return null;
                }

                @Override
                public jakarta.servlet.AsyncContext startAsync(jakarta.servlet.ServletRequest servletRequest, jakarta.servlet.ServletResponse servletResponse) {
                    return null;
                }

                @Override
                public boolean isAsyncStarted() {
                    return false;
                }

                @Override
                public boolean isAsyncSupported() {
                    return false;
                }

                @Override
                public jakarta.servlet.AsyncContext getAsyncContext() {
                    return null;
                }

                @Override
                public jakarta.servlet.DispatcherType getDispatcherType() {
                    return null;
                }

                @Override
                public String getRequestId() {
                    return null;
                }

                @Override
                public String getProtocolRequestId() {
                    return null;
                }

                @Override
                public jakarta.servlet.ServletConnection getServletConnection() {
                    return null;
                }

                @Override
                public String getAuthType() {
                    return null;
                }

                @Override
                public jakarta.servlet.http.Cookie[] getCookies() {
                    return null;
                }

                @Override
                public long getDateHeader(String name) {
                    return 0;
                }

                @Override
                public String getHeader(String name) {
                    return null;
                }

                @Override
                public java.util.Enumeration<String> getHeaders(String name) {
                    return null;
                }

                @Override
                public java.util.Enumeration<String> getHeaderNames() {
                    return null;
                }

                @Override
                public int getIntHeader(String name) {
                    return 0;
                }

                @Override
                public String getMethod() {
                    return null;
                }

                @Override
                public String getPathInfo() {
                    return null;
                }

                @Override
                public String getPathTranslated() {
                    return null;
                }

                @Override
                public String getContextPath() {
                    return null;
                }

                @Override
                public String getQueryString() {
                    return null;
                }

                @Override
                public String getRemoteUser() {
                    return null;
                }

                @Override
                public boolean isUserInRole(String role) {
                    return false;
                }

                @Override
                public java.security.Principal getUserPrincipal() {
                    return null;
                }

                @Override
                public String getRequestedSessionId() {
                    return null;
                }

                @Override
                public String getRequestURI() {
                    return null;
                }

                @Override
                public StringBuffer getRequestURL() {
                    return null;
                }

                @Override
                public String getServletPath() {
                    return null;
                }

                @Override
                public jakarta.servlet.http.HttpSession getSession(boolean create) {
                    return null;
                }

                @Override
                public jakarta.servlet.http.HttpSession getSession() {
                    return null;
                }

                @Override
                public String changeSessionId() {
                    return null;
                }

                @Override
                public boolean isRequestedSessionIdValid() {
                    return false;
                }

                @Override
                public boolean isRequestedSessionIdFromCookie() {
                    return false;
                }

                @Override
                public boolean isRequestedSessionIdFromURL() {
                    return false;
                }

                @Override
                public boolean authenticate(jakarta.servlet.http.HttpServletResponse response) {
                    return false;
                }

                @Override
                public void login(String username, String password) {
                }

                @Override
                public void logout() {
                }

                @Override
                public java.util.Collection<jakarta.servlet.http.Part> getParts() {
                    return null;
                }

                @Override
                public jakarta.servlet.http.Part getPart(String name) {
                    return null;
                }

                @Override
                public <T extends jakarta.servlet.http.HttpUpgradeHandler> T upgrade(Class<T> handlerClass) {
                    return null;
                }
            };

            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(faultyRequest));

            // When
            String clientIp = ClientIpExtractor.getClientIp();

            // Then
            assertThat(clientIp).isEqualTo("127.0.0.1");
        }
    }
}
