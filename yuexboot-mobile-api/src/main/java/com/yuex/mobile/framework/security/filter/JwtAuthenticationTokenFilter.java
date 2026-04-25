package com.yuex.mobile.framework.security.filter;

import com.yuex.mobile.framework.security.LoginUserDetail;
import com.yuex.mobile.framework.security.service.TokenService;
import com.yuex.util.util.ThreadMdcUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

@Slf4j
@Component
@AllArgsConstructor
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    private TokenService tokenService;

    // 定义需要放行的路径前缀
    // 定义需要放行的路径前缀
    private static final String[] IGNORE_PATHS = {
            "/upload/",
            "/common/download",
            "/doc.html",
            "/swagger-ui/",
            "/swagger-resources/",
            "/webjars/",
            "/druid/",
            "/callback/",
            "/pay/callback/",
            "/login",
            "/registry",
            "/genMobileCode",
            "/captcha",
            "/home/",
            "/category/",
            "/comment/",
            "/search/",
            "/goods/detail/",
            "/cart/goodsCount",
            "/diamond/",
            "/coupon/list",
            "/mall/",
            "/index",
            "favicon.ico"
    };


    private boolean isIgnorePath(String uri) {
        // 不可使用 startsWith("/")：会匹配所有 URI，导致 JWT 永不被解析
        if ("/".equals(uri)) {
            return true;
        }
        for (String path : IGNORE_PATHS) {
            if (uri.startsWith(path)) {
                return true;
            }
        }
        return false;
    }
@Override
protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain chain) throws ServletException, IOException {
    String uri = request.getRequestURI();

    if (isIgnorePath(uri)) {
        chain.doFilter(request, response);
        return;
    }

    ThreadMdcUtil.setTraceIdIfAbsent();
    try {
        // 仅在此处解析/续期 Token；业务接口抛出的异常不得在此处被误转为 401（例如支付回调验签失败应走控制器）
        LoginUserDetail loginUser = tokenService.getLoginUser(request);
        if (Objects.nonNull(loginUser) && Objects.isNull(SecurityContextHolder.getContext().getAuthentication())) {
            try {
                tokenService.verifyToken(loginUser);
            } catch (Exception e) {
                log.warn("Token 续期失败（忽略）: {}", e.getMessage());
            }
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    loginUser, null, loginUser.getAuthorities());
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            log.debug("已认证请求 uri={}, userId={}", uri,
                    loginUser.getMember() != null ? loginUser.getMember().getId() : null);
        }
    } catch (Exception e) {
        log.warn("JWT 过滤器解析异常（不中断请求）: {}", e.getMessage());
    }
    try {
        chain.doFilter(request, response);
    } finally {
        ThreadMdcUtil.removeTraceId();
    }
    }
}