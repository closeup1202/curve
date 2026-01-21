package com.project.curve.spring.context.actor;

import com.project.curve.core.envelope.EventActor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultActorContextProvider 테스트")
class DefaultActorContextProviderTest {

    @Mock
    private HttpServletRequest request;

    private DefaultActorContextProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DefaultActorContextProvider();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    @DisplayName("사용자 정보 테스트")
    class UserInfoTest {

        @Test
        @DisplayName("항상 SYSTEM 사용자를 반환한다")
        void getActor_shouldReturnSystemUser() {
            // Given
            setUpRequestContext("192.168.1.100");

            // When
            EventActor actor = provider.getActor();

            // Then
            assertThat(actor.userId()).isEqualTo("SYSTEM");
            assertThat(actor.role()).isEqualTo("ROLE_SYSTEM");
        }
    }

    @Nested
    @DisplayName("클라이언트 IP 테스트")
    class ClientIpTest {

        @Test
        @DisplayName("Request Context가 있으면 remoteAddr를 반환한다")
        void getActor_withRequestContext_shouldReturnRemoteAddr() {
            // Given
            setUpRequestContext("10.0.0.1");

            // When
            EventActor actor = provider.getActor();

            // Then
            assertThat(actor.ip()).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("Request Context가 없으면 기본 IP(127.0.0.1)를 반환한다")
        void getActor_withoutRequestContext_shouldReturnDefaultIp() {
            // Given - No request context set

            // When
            EventActor actor = provider.getActor();

            // Then
            assertThat(actor.ip()).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("remoteAddr가 null이면 기본 IP를 반환한다")
        void getActor_withNullRemoteAddr_shouldReturnDefaultIp() {
            // Given
            setUpRequestContext(null);

            // When
            EventActor actor = provider.getActor();

            // Then
            assertThat(actor.ip()).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("remoteAddr가 빈 문자열이면 기본 IP를 반환한다")
        void getActor_withEmptyRemoteAddr_shouldReturnDefaultIp() {
            // Given
            setUpRequestContext("");

            // When
            EventActor actor = provider.getActor();

            // Then
            assertThat(actor.ip()).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("remoteAddr가 'unknown'이면 기본 IP를 반환한다")
        void getActor_withUnknownRemoteAddr_shouldReturnDefaultIp() {
            // Given
            setUpRequestContext("unknown");

            // When
            EventActor actor = provider.getActor();

            // Then
            assertThat(actor.ip()).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("IPv6 주소도 정상적으로 반환된다")
        void getActor_withIpv6Address_shouldReturnIpv6() {
            // Given
            setUpRequestContext("::1");

            // When
            EventActor actor = provider.getActor();

            // Then
            assertThat(actor.ip()).isEqualTo("::1");
        }

        @Test
        @DisplayName("X-Forwarded-For로 처리된 IP도 정상 반환된다")
        void getActor_withForwardedIp_shouldReturnForwardedIp() {
            // Given - ForwardedHeaderFilter가 처리한 후의 IP
            setUpRequestContext("203.0.113.42");

            // When
            EventActor actor = provider.getActor();

            // Then
            assertThat(actor.ip()).isEqualTo("203.0.113.42");
        }
    }

    @Nested
    @DisplayName("일관성 테스트")
    class ConsistencyTest {

        @Test
        @DisplayName("여러 번 호출해도 일관된 결과를 반환한다")
        void getActor_calledMultipleTimes_shouldReturnConsistentResults() {
            // Given
            setUpRequestContext("192.168.1.1");

            // When
            EventActor actor1 = provider.getActor();
            EventActor actor2 = provider.getActor();

            // Then
            assertThat(actor1.userId()).isEqualTo(actor2.userId());
            assertThat(actor1.role()).isEqualTo(actor2.role());
            assertThat(actor1.ip()).isEqualTo(actor2.ip());
        }
    }

    private void setUpRequestContext(String remoteAddr) {
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
    }
}
