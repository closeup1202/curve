package com.project.curve.core.context;

import java.util.Map;

/**
 * 현재 실행 컨텍스트에서 추가적인 태그 정보를 제공하는 인터페이스.
 * <p>
 * 예: MDC에 저장된 커스텀 키-값 쌍을 추출하여 이벤트 메타데이터에 포함.
 */
public interface TagsContextProvider {
    /**
     * 현재 컨텍스트의 태그 정보를 반환합니다.
     *
     * @return 태그 맵 (Key-Value)
     */
    Map<String, String> getTags();
}