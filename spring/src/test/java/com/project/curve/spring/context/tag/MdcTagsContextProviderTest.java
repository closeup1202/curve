package com.project.curve.spring.context.tag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MdcTagsContextProvider 테스트")
class MdcTagsContextProviderTest {

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Nested
    @DisplayName("기본 생성자 테스트")
    class DefaultConstructorTest {

        @Test
        @DisplayName("기본 생성자는 region과 tenant를 추출한다")
        void getTags_withDefaultKeys_shouldExtractRegionAndTenant() {
            // Given
            MDC.put("region", "ap-northeast-2");
            MDC.put("tenant", "company-001");
            MdcTagsContextProvider provider = new MdcTagsContextProvider();

            // When
            Map<String, String> tags = provider.getTags();

            // Then
            assertThat(tags)
                    .containsEntry("region", "ap-northeast-2")
                    .containsEntry("tenant", "company-001")
                    .hasSize(2);
        }

        @Test
        @DisplayName("MDC에 값이 없으면 빈 맵을 반환한다")
        void getTags_withNoMdcValues_shouldReturnEmptyMap() {
            // Given
            MdcTagsContextProvider provider = new MdcTagsContextProvider();

            // When
            Map<String, String> tags = provider.getTags();

            // Then
            assertThat(tags).isEmpty();
        }

        @Test
        @DisplayName("일부 키만 설정되어 있으면 해당 키만 반환한다")
        void getTags_withPartialValues_shouldReturnOnlySetKeys() {
            // Given
            MDC.put("region", "us-east-1");
            // tenant is not set
            MdcTagsContextProvider provider = new MdcTagsContextProvider();

            // When
            Map<String, String> tags = provider.getTags();

            // Then
            assertThat(tags)
                    .containsEntry("region", "us-east-1")
                    .doesNotContainKey("tenant")
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("커스텀 키 테스트")
    class CustomKeysTest {

        @Test
        @DisplayName("withKeys로 커스텀 키를 설정할 수 있다")
        void withKeys_shouldUseCustomKeys() {
            // Given
            MDC.put("customKey1", "value1");
            MDC.put("customKey2", "value2");
            MdcTagsContextProvider provider = MdcTagsContextProvider.withKeys("customKey1", "customKey2");

            // When
            Map<String, String> tags = provider.getTags();

            // Then
            assertThat(tags)
                    .containsEntry("customKey1", "value1")
                    .containsEntry("customKey2", "value2")
                    .hasSize(2);
        }

        @Test
        @DisplayName("커스텀 키가 설정되면 기본 키는 무시된다")
        void withKeys_shouldIgnoreDefaultKeys() {
            // Given
            MDC.put("region", "ap-northeast-2");
            MDC.put("customKey", "customValue");
            MdcTagsContextProvider provider = MdcTagsContextProvider.withKeys("customKey");

            // When
            Map<String, String> tags = provider.getTags();

            // Then
            assertThat(tags)
                    .containsEntry("customKey", "customValue")
                    .doesNotContainKey("region")
                    .hasSize(1);
        }

        @Test
        @DisplayName("빈 키 배열로 생성하면 빈 맵을 반환한다")
        void withKeys_withEmptyArray_shouldReturnEmptyMap() {
            // Given
            MDC.put("region", "ap-northeast-2");
            MdcTagsContextProvider provider = MdcTagsContextProvider.withKeys();

            // When
            Map<String, String> tags = provider.getTags();

            // Then
            assertThat(tags).isEmpty();
        }
    }

    @Nested
    @DisplayName("불변성 테스트")
    class ImmutabilityTest {

        @Test
        @DisplayName("반환된 맵은 불변이다")
        void getTags_shouldReturnImmutableMap() {
            // Given
            MDC.put("region", "ap-northeast-2");
            MdcTagsContextProvider provider = new MdcTagsContextProvider();

            // When
            Map<String, String> tags = provider.getTags();

            // Then
            assertThatThrownBy(() -> tags.put("newKey", "newValue"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("tagKeys는 불변이다")
        void tagKeys_shouldBeImmutable() {
            // Given
            MdcTagsContextProvider provider = new MdcTagsContextProvider();

            // When & Then
            assertThatThrownBy(() -> provider.tagKeys().add("newKey"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("null 안전성 테스트")
    class NullSafetyTest {

        @Test
        @DisplayName("MDC 값이 null이어도 NPE가 발생하지 않는다")
        void getTags_withNullMdcValue_shouldNotThrowNpe() {
            // Given
            // MDC.get() returns null for non-existent keys
            MdcTagsContextProvider provider = new MdcTagsContextProvider();

            // When & Then
            assertThatCode(() -> provider.getTags()).doesNotThrowAnyException();
        }
    }
}
