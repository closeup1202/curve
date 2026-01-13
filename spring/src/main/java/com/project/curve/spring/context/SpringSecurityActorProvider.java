package com.project.curve.spring.context;

import com.project.curve.core.context.ActorContextProvider;
import com.project.curve.core.envelope.EventActor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SpringSecurityActorProvider implements ActorContextProvider {

    @Override
    public EventActor getActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            return new EventActor("SYSTEM", "ROLE_SYSTEM", "127.0.0.1");
        }

        String userId = auth.getName();

        // 권한 정보 (첫 번째 권한 추출 예시)
        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(Object::toString)
                .orElse("ROLE_USER");

        return new EventActor(userId, role, "unknown");
    }
}
