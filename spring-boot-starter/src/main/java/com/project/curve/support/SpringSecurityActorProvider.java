//package com.project.curve.support;
//
//public class SpringSecurityActorProvider implements ActorContextProvider {
//
//    @Override
//    public EventActor getActor() {
//        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
//
//        if (auth == null || !auth.isAuthenticated()) {
//            return new EventActor("SYSTEM", "ROLE_SYSTEM", "127.0.0.1");
//        }
//
//        // 유저 식별자 (보통 username이나 ID)
//        String userId = auth.getName();
//        // 권한 정보 (첫 번째 권한 추출 예시)
//        String role = auth.getAuthorities().stream()
//                .findFirst()
//                .map(Object::toString)
//                .orElse("ROLE_USER");
//
//        // IP의 경우 HttpServletRequest가 필요할 수 있으나,
//        // 여기서는 기본 정보를 담는 구조로 설계
//        return new EventActor(userId, role, "unknown");
//    }
//}
