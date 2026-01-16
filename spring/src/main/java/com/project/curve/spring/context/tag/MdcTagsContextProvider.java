package com.project.curve.spring.context.tag;

import com.project.curve.core.context.TagsContextProvider;
import org.slf4j.MDC;

import java.util.*;

/**
 * MDC(Mapped Diagnostic Context) 기반 Tags Context Provider
 *
 * <p>SLF4J의 MDC에서 특정 키를 읽어 이벤트 메타데이터 태그로 전달합니다.</p>
 *
 * <h3>사용 예시</h3>
 * <pre>
 * // 요청 처리 시작 시 MDC 설정
 * MDC.put("region", "ap-northeast-2");
 * MDC.put("tenant", "company-001");
 *
 * // 이벤트 발행 시 자동으로 tags에 포함됨
 * eventProducer.publish(payload);
 *
 * // 요청 처리 종료 시 MDC 정리
 * MDC.clear();
 * </pre>
 *
 * <h3>커스터마이징</h3>
 * <pre>
 * // 다른 키를 사용하려면:
 * {@literal @}Bean
 * public TagsContextProvider tagsContextProvider() {
 *     return MdcTagsContextProvider.withKeys("region", "tenant", "customKey");
 * }
 * </pre>
 *
 * <h3>주의사항</h3>
 * <ul>
 *   <li>MDC는 ThreadLocal 기반이므로 비동기 처리 시 전파 필요</li>
 *   <li>값이 null인 키는 자동으로 제외됨 (NPE 방지)</li>
 *   <li>빈 맵인 경우에도 안전하게 처리됨</li>
 * </ul>
 */
public record MdcTagsContextProvider(List<String> tagKeys) implements TagsContextProvider {

    /**
     * MDC에서 추출할 키 목록 (기본값: region, tenant)
     */
    private static final List<String> DEFAULT_TAG_KEYS = List.of("region", "tenant");

    /**
     * 기본 생성자 (region, tenant 사용)
     */
    public MdcTagsContextProvider() {
        this(DEFAULT_TAG_KEYS);
    }

    /**
     * 커스텀 키를 사용하는 생성자
     *
     * @param tagKeys MDC에서 추출할 키 목록
     */
    public MdcTagsContextProvider(List<String> tagKeys) {
        this.tagKeys = List.copyOf(tagKeys);
    }

    @Override
    public Map<String, String> getTags() {
        Map<String, String> tags = new HashMap<>();

        // null 체크를 통한 NPE 방지
        for (String key : tagKeys) {
            Optional.ofNullable(MDC.get(key))
                    .ifPresent(value -> tags.put(key, value));
        }

        // 빈 맵인 경우 불변 빈 맵 반환, 아니면 불변 맵으로 변환
        return tags.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(tags);
    }

    /**
     * 커스텀 태그 키를 사용하는 Provider 생성 (정적 팩토리 메서드)
     *
     * @param tagKeys MDC에서 추출할 키 목록
     * @return 커스텀 MdcTagsContextProvider
     */
    public static MdcTagsContextProvider withKeys(String... tagKeys) {
        return new MdcTagsContextProvider(Arrays.asList(tagKeys));
    }
}
