package cn.hollis.llm.mentor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String FIXED_TOKEN = "abc123456789";
    private static final String AUTH_HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String header = request.getHeader(AUTH_HEADER);

        // 判断 header 是否存在
        if (header == null || !header.startsWith(PREFIX)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Missing Authorization header");
            return false;
        }

        // 提取 token
        String token = header.substring(PREFIX.length());

        // 校验 token
        if (!FIXED_TOKEN.equals(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid token");
            return false;
        }

        // 校验成功
        return true;
    }
}
