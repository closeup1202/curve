package com.project.curve.spring.audit.aop;

import com.project.curve.core.port.EventProducer;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.spring.audit.annotation.PublishEvent;
import com.project.curve.spring.audit.payload.EventPayload;
import com.project.curve.spring.exception.EventPublishException;
import com.project.curve.spring.metrics.CurveMetricsCollector;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PublishEventAspect 테스트")
class PublishEventAspectTest {

    @Mock
    private EventProducer eventProducer;

    @Mock
    private CurveMetricsCollector metricsCollector;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private PublishEvent publishEvent;

    @Captor
    private ArgumentCaptor<EventPayload> payloadCaptor;

    @Captor
    private ArgumentCaptor<EventSeverity> severityCaptor;

    private PublishEventAspect aspect;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        aspect = new PublishEventAspect(eventProducer);

        // 기본 JoinPoint 설정
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getDeclaringType()).thenReturn((Class) TestService.class);
        when(methodSignature.getName()).thenReturn("testMethod");

        Method method = TestService.class.getMethod("testMethod", String.class);
        when(methodSignature.getMethod()).thenReturn(method);
    }

    @Nested
    @DisplayName("Phase.BEFORE 테스트")
    class BeforePhaseTest {

        @Test
        @DisplayName("BEFORE phase에서 메서드 실행 전에 이벤트를 발행한다")
        void beforeMethod_withBeforePhase_shouldPublishEvent() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.BEFORE);
            when(publishEvent.eventType()).thenReturn("TEST_EVENT");
            when(publishEvent.severity()).thenReturn(EventSeverity.INFO);
            when(publishEvent.payloadIndex()).thenReturn(0);
            when(joinPoint.getArgs()).thenReturn(new Object[]{"testData"});

            // When
            aspect.beforeMethod(joinPoint, publishEvent);

            // Then
            verify(eventProducer).publish(payloadCaptor.capture(), severityCaptor.capture());

            EventPayload payload = payloadCaptor.getValue();
            assertThat(payload.getEventType()).isEqualTo("TEST_EVENT");
            assertThat(payload.getData()).isEqualTo("testData");
            assertThat(severityCaptor.getValue()).isEqualTo(EventSeverity.INFO);
        }

        @Test
        @DisplayName("AFTER_RETURNING phase에서는 beforeMethod가 이벤트를 발행하지 않는다")
        void beforeMethod_withAfterReturningPhase_shouldNotPublishEvent() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER_RETURNING);

            // When
            aspect.beforeMethod(joinPoint, publishEvent);

            // Then
            verifyNoInteractions(eventProducer);
        }
    }

    @Nested
    @DisplayName("Phase.AFTER_RETURNING 테스트")
    class AfterReturningPhaseTest {

        @Test
        @DisplayName("AFTER_RETURNING phase에서 반환값을 페이로드로 사용한다")
        void afterReturning_withAfterReturningPhase_shouldUseReturnValueAsPayload() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER_RETURNING);
            when(publishEvent.eventType()).thenReturn("ORDER_CREATED");
            when(publishEvent.severity()).thenReturn(EventSeverity.INFO);
            when(publishEvent.payloadIndex()).thenReturn(-1); // 반환값 사용
            Object returnValue = new TestOrder("order-123");

            // When
            aspect.afterReturning(joinPoint, publishEvent, returnValue);

            // Then
            verify(eventProducer).publish(payloadCaptor.capture(), eq(EventSeverity.INFO));

            EventPayload payload = payloadCaptor.getValue();
            assertThat(payload.getEventType()).isEqualTo("ORDER_CREATED");
            assertThat(payload.getData()).isEqualTo(returnValue);
        }

        @Test
        @DisplayName("BEFORE phase에서는 afterReturning이 이벤트를 발행하지 않는다")
        void afterReturning_withBeforePhase_shouldNotPublishEvent() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.BEFORE);

            // When
            aspect.afterReturning(joinPoint, publishEvent, "result");

            // Then
            verifyNoInteractions(eventProducer);
        }
    }

    @Nested
    @DisplayName("Phase.AFTER 테스트")
    class AfterPhaseTest {

        @Test
        @DisplayName("AFTER phase에서 메서드 실행 후 이벤트를 발행한다")
        void afterMethod_withAfterPhase_shouldPublishEvent() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER);
            when(publishEvent.eventType()).thenReturn("AFTER_EVENT");
            when(publishEvent.severity()).thenReturn(EventSeverity.WARN);
            when(publishEvent.payloadIndex()).thenReturn(-1);

            // When
            aspect.afterMethod(joinPoint, publishEvent);

            // Then
            verify(eventProducer).publish(payloadCaptor.capture(), eq(EventSeverity.WARN));
            assertThat(payloadCaptor.getValue().getEventType()).isEqualTo("AFTER_EVENT");
        }
    }

    @Nested
    @DisplayName("eventType 결정 테스트")
    class EventTypeDeterminationTest {

        @Test
        @DisplayName("eventType이 지정되지 않으면 클래스명.메서드명을 사용한다")
        void determineEventType_withBlankEventType_shouldUseClassName() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER_RETURNING);
            when(publishEvent.eventType()).thenReturn(""); // 빈 문자열
            when(publishEvent.severity()).thenReturn(EventSeverity.INFO);
            when(publishEvent.payloadIndex()).thenReturn(-1);

            // When
            aspect.afterReturning(joinPoint, publishEvent, "result");

            // Then
            verify(eventProducer).publish(payloadCaptor.capture(), any());
            assertThat(payloadCaptor.getValue().getEventType()).isEqualTo("TestService.testMethod");
        }
    }

    @Nested
    @DisplayName("payloadIndex 테스트")
    class PayloadIndexTest {

        @Test
        @DisplayName("payloadIndex가 0이면 첫 번째 파라미터를 사용한다")
        void extractPayload_withPayloadIndex0_shouldUseFirstArg() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER_RETURNING);
            when(publishEvent.eventType()).thenReturn("TEST");
            when(publishEvent.severity()).thenReturn(EventSeverity.INFO);
            when(publishEvent.payloadIndex()).thenReturn(0);
            when(joinPoint.getArgs()).thenReturn(new Object[]{"firstArg", "secondArg"});

            // When
            aspect.afterReturning(joinPoint, publishEvent, "returnValue");

            // Then
            verify(eventProducer).publish(payloadCaptor.capture(), any());
            assertThat(payloadCaptor.getValue().getData()).isEqualTo("firstArg");
        }

        @Test
        @DisplayName("유효하지 않은 payloadIndex는 null을 반환한다")
        void extractPayload_withInvalidPayloadIndex_shouldReturnNull() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER_RETURNING);
            when(publishEvent.eventType()).thenReturn("TEST");
            when(publishEvent.severity()).thenReturn(EventSeverity.INFO);
            when(publishEvent.payloadIndex()).thenReturn(99); // 유효하지 않은 인덱스
            when(joinPoint.getArgs()).thenReturn(new Object[]{"arg"});

            // When
            aspect.afterReturning(joinPoint, publishEvent, "returnValue");

            // Then
            verify(eventProducer).publish(payloadCaptor.capture(), any());
            assertThat(payloadCaptor.getValue().getData()).isNull();
        }
    }

    @Nested
    @DisplayName("예외 처리 테스트")
    class ExceptionHandlingTest {

        @Test
        @DisplayName("failOnError가 true일 때 예외가 발생하면 EventPublishException을 던진다")
        void handlePublishFailure_withFailOnErrorTrue_shouldThrowException() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER_RETURNING);
            when(publishEvent.eventType()).thenReturn("TEST");
            when(publishEvent.severity()).thenReturn(EventSeverity.INFO);
            when(publishEvent.payloadIndex()).thenReturn(-1);
            when(publishEvent.failOnError()).thenReturn(true);

            doThrow(new RuntimeException("Kafka connection failed"))
                    .when(eventProducer).publish(any(), any());

            // When & Then
            assertThatThrownBy(() -> aspect.afterReturning(joinPoint, publishEvent, "result"))
                    .isInstanceOf(EventPublishException.class)
                    .hasMessageContaining("Failed to publish event");
        }

        @Test
        @DisplayName("failOnError가 false일 때 예외가 발생해도 비즈니스 로직은 계속된다")
        void handlePublishFailure_withFailOnErrorFalse_shouldContinue() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER_RETURNING);
            when(publishEvent.eventType()).thenReturn("TEST");
            when(publishEvent.severity()).thenReturn(EventSeverity.INFO);
            when(publishEvent.payloadIndex()).thenReturn(-1);
            when(publishEvent.failOnError()).thenReturn(false);

            doThrow(new RuntimeException("Kafka connection failed"))
                    .when(eventProducer).publish(any(), any());

            // When & Then - 예외가 발생하지 않아야 함
            assertThatCode(() -> aspect.afterReturning(joinPoint, publishEvent, "result"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("심각도(Severity) 테스트")
    class SeverityTest {

        @Test
        @DisplayName("CRITICAL 심각도로 이벤트를 발행할 수 있다")
        void publishEvent_withCriticalSeverity_shouldPublish() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER_RETURNING);
            when(publishEvent.eventType()).thenReturn("CRITICAL_EVENT");
            when(publishEvent.severity()).thenReturn(EventSeverity.CRITICAL);
            when(publishEvent.payloadIndex()).thenReturn(-1);

            // When
            aspect.afterReturning(joinPoint, publishEvent, "criticalData");

            // Then
            verify(eventProducer).publish(any(), eq(EventSeverity.CRITICAL));
        }

        @Test
        @DisplayName("ERROR 심각도로 이벤트를 발행할 수 있다")
        void publishEvent_withErrorSeverity_shouldPublish() {
            // Given
            when(publishEvent.phase()).thenReturn(PublishEvent.Phase.AFTER_RETURNING);
            when(publishEvent.eventType()).thenReturn("ERROR_EVENT");
            when(publishEvent.severity()).thenReturn(EventSeverity.ERROR);
            when(publishEvent.payloadIndex()).thenReturn(-1);

            // When
            aspect.afterReturning(joinPoint, publishEvent, "errorData");

            // Then
            verify(eventProducer).publish(any(), eq(EventSeverity.ERROR));
        }
    }

    // 테스트용 클래스
    public static class TestService {
        public String testMethod(String input) {
            return "result: " + input;
        }
    }

    public record TestOrder(String orderId) {}
}
