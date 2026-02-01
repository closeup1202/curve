package com.project.curve.core.context;

import com.project.curve.core.envelope.EventActor;

/**
 * 현재 실행 컨텍스트에서 이벤트 주체(Actor) 정보를 제공하는 인터페이스.
 * <p>
 * 예: Spring SecurityContext에서 현재 로그인한 사용자 정보를 추출.
 */
public interface ActorContextProvider {
    /**
     * 현재 주체(Actor) 정보를 반환합니다.
     *
     * @return 이벤트 주체 정보
     */
    EventActor getActor();
}
