package com.project.curve.spring.pii.jackson;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.processor.PiiProcessorRegistry;
import com.project.curve.spring.pii.strategy.PiiStrategy;
import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PiiBeanSerializerModifier Test")
@ExtendWith(MockitoExtension.class)
class PiiBeanSerializerModifierTest {

    @Mock
    private PiiProcessorRegistry processorRegistry;

    @Mock
    private SerializationConfig config;

    @Mock
    private BeanDescription beanDesc;

    private PiiBeanSerializerModifier modifier;

    @BeforeEach
    void setUp() {
        modifier = new PiiBeanSerializerModifier(processorRegistry);
    }

    @Nested
    @DisplayName("Change Properties")
    class ChangeProperties {

        @Test
        @DisplayName("Should replace property writer for @PiiField annotated field")
        void changeProperties_withPiiField_shouldReplacWriter() {
            // Given
            when(beanDesc.getBeanClass()).thenReturn((Class) TestBean.class);

            BeanPropertyWriter emailWriter = mock(BeanPropertyWriter.class);
            when(emailWriter.getName()).thenReturn("email");

            List<BeanPropertyWriter> writers = new ArrayList<>();
            writers.add(emailWriter);

            // When
            List<BeanPropertyWriter> result = modifier.changeProperties(config, beanDesc, writers);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOf(PiiPropertyWriter.class);
        }

        @Test
        @DisplayName("Should keep original writer for non-@PiiField field")
        void changeProperties_withoutPiiField_shouldKeepOriginal() {
            // Given
            when(beanDesc.getBeanClass()).thenReturn((Class) TestBean.class);

            BeanPropertyWriter plainWriter = mock(BeanPropertyWriter.class);
            when(plainWriter.getName()).thenReturn("plainField");

            List<BeanPropertyWriter> writers = new ArrayList<>();
            writers.add(plainWriter);

            // When
            List<BeanPropertyWriter> result = modifier.changeProperties(config, beanDesc, writers);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isSameAs(plainWriter);
        }

        @Test
        @DisplayName("Should handle multiple properties correctly")
        void changeProperties_withMultipleProperties_shouldProcessAll() {
            // Given
            when(beanDesc.getBeanClass()).thenReturn((Class) TestBean.class);

            BeanPropertyWriter emailWriter = mock(BeanPropertyWriter.class);
            when(emailWriter.getName()).thenReturn("email");

            BeanPropertyWriter plainWriter = mock(BeanPropertyWriter.class);
            when(plainWriter.getName()).thenReturn("plainField");

            BeanPropertyWriter ssnWriter = mock(BeanPropertyWriter.class);
            when(ssnWriter.getName()).thenReturn("ssn");

            List<BeanPropertyWriter> writers = new ArrayList<>();
            writers.add(emailWriter);
            writers.add(plainWriter);
            writers.add(ssnWriter);

            // When
            List<BeanPropertyWriter> result = modifier.changeProperties(config, beanDesc, writers);

            // Then
            assertThat(result).hasSize(3);
            assertThat(result.get(0)).isInstanceOf(PiiPropertyWriter.class); // email
            assertThat(result.get(1)).isSameAs(plainWriter); // plainField
            assertThat(result.get(2)).isInstanceOf(PiiPropertyWriter.class); // ssn
        }
    }

    @Nested
    @DisplayName("Annotation Detection")
    class AnnotationDetection {

        @Test
        @DisplayName("Should detect @PiiField on direct field")
        void findPiiField_onDirectField_shouldDetect() {
            // Given
            when(beanDesc.getBeanClass()).thenReturn((Class) TestBean.class);

            BeanPropertyWriter writer = mock(BeanPropertyWriter.class);
            when(writer.getName()).thenReturn("email");

            // When
            List<BeanPropertyWriter> result = modifier.changeProperties(
                    config,
                    beanDesc,
                    List.of(writer)
            );

            // Then
            assertThat(result.get(0)).isInstanceOf(PiiPropertyWriter.class);
        }

        @Test
        @DisplayName("Should detect @PiiField on inherited field")
        void findPiiField_onInheritedField_shouldDetect() {
            // Given
            when(beanDesc.getBeanClass()).thenReturn((Class) SubTestBean.class);

            BeanPropertyWriter writer = mock(BeanPropertyWriter.class);
            when(writer.getName()).thenReturn("email"); // Inherited from TestBean

            // When
            List<BeanPropertyWriter> result = modifier.changeProperties(
                    config,
                    beanDesc,
                    List.of(writer)
            );

            // Then
            assertThat(result.get(0)).isInstanceOf(PiiPropertyWriter.class);
        }

        @Test
        @DisplayName("Should handle record components")
        void findPiiField_onRecord_shouldDetect() {
            // Given
            when(beanDesc.getBeanClass()).thenReturn((Class) TestRecord.class);

            BeanPropertyWriter writer = mock(BeanPropertyWriter.class);
            when(writer.getName()).thenReturn("email");

            // When
            List<BeanPropertyWriter> result = modifier.changeProperties(
                    config,
                    beanDesc,
                    List.of(writer)
            );

            // Then
            assertThat(result.get(0)).isInstanceOf(PiiPropertyWriter.class);
        }

        @Test
        @DisplayName("Should return null for non-existent field")
        void findPiiField_nonExistentField_shouldReturnNull() {
            // Given
            when(beanDesc.getBeanClass()).thenReturn((Class) TestBean.class);

            BeanPropertyWriter writer = mock(BeanPropertyWriter.class);
            when(writer.getName()).thenReturn("nonExistentField");

            // When
            List<BeanPropertyWriter> result = modifier.changeProperties(
                    config,
                    beanDesc,
                    List.of(writer)
            );

            // Then
            assertThat(result.get(0)).isSameAs(writer); // Keep original
        }
    }

    @Nested
    @DisplayName("Empty and Null Cases")
    class EmptyAndNullCases {

        @Test
        @DisplayName("Should handle empty property list")
        void changeProperties_emptyList_shouldReturnEmpty() {
            // Given
            // No stubbing needed for empty list

            // When
            List<BeanPropertyWriter> result = modifier.changeProperties(
                    config,
                    beanDesc,
                    new ArrayList<>()
            );

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should handle bean with no PII fields")
        void changeProperties_noPiiFields_shouldKeepAllOriginal() {
            // Given
            when(beanDesc.getBeanClass()).thenReturn((Class) PlainBean.class);

            BeanPropertyWriter writer1 = mock(BeanPropertyWriter.class);
            when(writer1.getName()).thenReturn("field1");

            BeanPropertyWriter writer2 = mock(BeanPropertyWriter.class);
            when(writer2.getName()).thenReturn("field2");

            List<BeanPropertyWriter> writers = List.of(writer1, writer2);

            // When
            List<BeanPropertyWriter> result = modifier.changeProperties(
                    config,
                    beanDesc,
                    writers
            );

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isSameAs(writer1);
            assertThat(result.get(1)).isSameAs(writer2);
        }
    }

    // Test beans
    static class TestBean {
        @PiiField(strategy = PiiStrategy.MASK, type = PiiType.EMAIL, level = MaskingLevel.NORMAL)
        public String email;

        @PiiField(strategy = PiiStrategy.ENCRYPT)
        public String ssn;

        public String plainField;
    }

    static class SubTestBean extends TestBean {
        public String additionalField;
    }

    record TestRecord(
            @PiiField(strategy = PiiStrategy.MASK, type = PiiType.EMAIL, level = MaskingLevel.NORMAL)
            String email,
            String plainField
    ) {
    }

    static class PlainBean {
        public String field1;
        public String field2;
    }
}
