package com.project.curve.core.context;

import com.project.curve.core.envelope.EventSource;

/**
 * 현재 실행 컨텍스트에서 이벤트 발생처(Source) 정보를 제공하는 인터페이스.
 * <p>
 * 예: 현재 애플리케이션의 서비스명, 환경, 버전 정보 등을 제공.
 */
public interface SourceContextProvider {
    /**
     * 현재 발생처(Source) 정보를 반환합니다.
     *
     * @return 이벤트 발생처 정보
     */
    EventSource getSource();
}
