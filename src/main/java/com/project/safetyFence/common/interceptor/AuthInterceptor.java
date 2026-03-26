package com.project.safetyFence.common.interceptor;

import com.project.safetyFence.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler) throws Exception {

        // CORS preflight OPTIONS 요청은 인증 제외
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // 로그인/회원가입은 인증 제외
        String uri = request.getRequestURI();
        if (uri.equals("/user/signIn") || uri.equals("/user/signup")) {
            return true;
        }

        // API Key 확인
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"API Key가 필요합니다\"}");
            return false;
        }

        // API Key 검증
        String userNumber = userService.findUserNumberByApiKey(apiKey);
        if (userNumber == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"유효하지 않은 API Key입니다\"}");
            return false;
        }

        // Request에 사용자 정보 저장 (Controller에서 사용)
        request.setAttribute("userNumber", userNumber);

        return true;
    }
}
