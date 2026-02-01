package com.project.curve.core.envelope;

/**
 * 이벤트를 유발한 주체(Actor) 정보.
 *
 * @param id   사용자 ID 또는 시스템 ID
 * @param role 사용자 역할 (Role)
 * @param ip   클라이언트 IP 주소
 */
public record EventActor(
        String id,
        String role,
        String ip
) {
}
