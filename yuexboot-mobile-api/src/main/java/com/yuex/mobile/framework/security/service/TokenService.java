package com.yuex.mobile.framework.security.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.yuex.common.config.TokenConfig;
import com.yuex.data.redis.constant.CacheConstants;
import com.yuex.data.redis.manager.StringRedisCache;
import com.yuex.mobile.framework.security.LoginUserDetail;
import com.yuex.util.constant.SysConstants;
import com.yuex.util.util.jwt.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@AllArgsConstructor
public class TokenService {

    protected static final long MILLIS_SECOND = 1000;
    protected static final long MILLIS_MINUTE = 60 * MILLIS_SECOND;
    /** 与 admin 端一致：剩余有效期小于该值时再续期，避免会话仅数十分钟却按「5 天」判断导致每次请求都 refresh */
    private static final long REFRESH_WHEN_REMAINING_MS = 20 * 60 * MILLIS_SECOND;

    private StringRedisCache stringRedisCache;
    private TokenConfig config;

    /**
     * 从请求中获取登录用户
     */
    public LoginUserDetail getLoginUser(HttpServletRequest request) {
        String token = getToken(request);
        if (StringUtils.isEmpty(token)) {
            return null;
        }
        try {
            DecodedJWT decodedJWT = JwtUtil.parseToken(token);
            String sign = decodedJWT.getClaim(SysConstants.SIGN_KEY).asString();
            if (StringUtils.isEmpty(sign)) {
                return null;
            }
            String userKey = getTokenKey(sign);
            LoginUserDetail loginUser = stringRedisCache.getCacheObject(userKey, LoginUserDetail.class);
            if (loginUser == null) {
                log.debug("Redis 无登录会话 key={}", userKey);
            }
            return loginUser;
        } catch (Exception e) {
            log.debug("解析 Token 失败: {}", e.getMessage());
            return null;
        }
    }


    /**
     * 根据token获取登录用户
     */
    public LoginUserDetail getLoginUser(String token) {
        if (StringUtils.isNotEmpty(token)) {
            try {
                DecodedJWT decodedJWT = JwtUtil.parseToken(token);
                String sign = decodedJWT.getClaim(SysConstants.SIGN_KEY).asString();
                String userKey = getTokenKey(sign);
                return stringRedisCache.getCacheObject(userKey, LoginUserDetail.class);
            } catch (Exception e) {
                log.debug("解析 Token 失败: {}", e.getMessage());
                return null;
            }
        }
        return null;
    }

    /**
     * 创建 JWT（与 Redis 会话同周期）
     */
    public String createToken(LoginUserDetail loginUser) {
        String token = UUID.randomUUID().toString().replaceAll("-", "");
        loginUser.setToken(token);
        refreshToken(loginUser);
        long sessionMs = (long) config.getExpireTime() * MILLIS_MINUTE;
        return JwtUtil.sign(token, config.getSecret(), sessionMs);
    }

    /**
     * 删除登录用户
     */
    public void delLoginUser(String token) {
        if (StringUtils.isNotEmpty(token)) {
            String userKey = getTokenKey(token);
            stringRedisCache.deleteObject(userKey);
            log.debug("删除 Token 缓存 key={}", userKey);
        }
    }

    /**
     * 刷新Token
     */
    public void refreshToken(LoginUserDetail loginUser) {
        loginUser.setLoginTime(System.currentTimeMillis());
        // ✅ 修复：使用分钟而不是天
        loginUser.setExpireTime(loginUser.getLoginTime() + config.getExpireTime() * MILLIS_MINUTE);

        // 根据uuid将loginUser缓存
        String userKey = CacheConstants.LOGIN_TOKEN_KEY + loginUser.getToken();
        // ✅ 修复：使用分钟作为时间单位
        stringRedisCache.setCacheObject(userKey, loginUser, config.getExpireTime(), TimeUnit.MINUTES);

        log.debug("刷新 Token 缓存 key={}, TTL={} 分钟", userKey, config.getExpireTime());
    }

    /**
     * 会话将到期前续期 Redis（与 admin 逻辑一致，按剩余分钟数判断，不按「自然日」）
     */
    public void verifyToken(LoginUserDetail loginUser) {
        if (loginUser == null) {
            return;
        }
        long expireTime = loginUser.getExpireTime();
        if (expireTime <= 0) {
            return;
        }
        long ttlLeft = expireTime - System.currentTimeMillis();
        long sessionMs = (long) config.getExpireTime() * MILLIS_MINUTE;
        // 极短会话时：剩余不足会话时长的 1/3 且小于 20 分钟窗口时再刷新，避免每次请求都写 Redis
        long threshold = Math.min(REFRESH_WHEN_REMAINING_MS, Math.max(5 * MILLIS_MINUTE, sessionMs / 3));
        if (ttlLeft > 0 && ttlLeft <= threshold) {
            log.debug("Token 临近过期，续期 Redis，用户: {}", loginUser.getUsername());
            refreshToken(loginUser);
        }
    }

    /**
     * 获取请求头中的token
     *
     * @param request 请求
     * @return token
     */
    private String getToken(HttpServletRequest request) {
        String token = request.getHeader(config.getHeader());
        // 强制去掉 Bearer 前缀（不管常量是什么，直接硬修复）
        if (StringUtils.isNotEmpty(token) && token.startsWith(SysConstants.TOKEN_PREFIX)) {
            token = token.substring(SysConstants.TOKEN_PREFIX.length());
        }
        return token;
    }

    /**
     * 获取缓存中用户的key
     *
     * @param sign 签名
     * @return 返回token的key
     */
    private String getTokenKey(String sign) {
        return CacheConstants.LOGIN_TOKEN_KEY + sign;
    }
}