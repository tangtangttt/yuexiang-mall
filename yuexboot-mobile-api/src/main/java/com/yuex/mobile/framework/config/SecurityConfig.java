package com.yuex.mobile.framework.config;

import com.yuex.mobile.framework.security.filter.JwtAuthenticationTokenFilter;
import com.yuex.mobile.framework.security.handle.AuthenticationEntryPointImpl;
import com.yuex.mobile.framework.security.handle.LogoutSuccessHandlerImpl;
import com.yuex.mobile.framework.security.service.UserDetailsServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@AllArgsConstructor
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

    private UserDetailsServiceImpl userDetailsService;
    private AuthenticationEntryPointImpl unauthorizedHandler;
    private JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter;
    private LogoutSuccessHandlerImpl logoutSuccessHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(configurer -> configurer.authenticationEntryPoint(unauthorizedHandler))
                .sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        // 核心：这里放行 根目录 + 前端页面 + 登录 + 静态资源
                        .requestMatchers("/", "/mall/**", "/index", "favicon.ico").permitAll()
                        .requestMatchers("/actuator/**", "/login", "/registry", "/genMobileCode", "/captcha").permitAll()
                        .requestMatchers("/test/**", "/seckill/**").permitAll()
                        .requestMatchers("/home/**", "/category/**", "/comment/**", "/search/**").permitAll()
                        .requestMatchers("/goods/detail/**", "/cart/goodsCount", "/diamond/**", "/coupon/list").permitAll()
                        .requestMatchers("wx/jsSdkInit").permitAll()
                        .requestMatchers("/upload/**", "/common/download**").permitAll()
                        .requestMatchers("/doc.html", "/swagger-ui/**", "/swagger-resources/**", "/webjars/**").permitAll()
                        .requestMatchers("/*/api-docs", "/druid/**").permitAll()
                        // 支付回调：支付宝/微信服务器 POST，无登录态；须与 PayNotifyController 的 /pay/callback/** 一致
                        .requestMatchers("/callback/**", "/pay/callback/**").permitAll()
                        .anyRequest().authenticated()
                )
                .logout(configurer -> configurer.logoutUrl("/logout").logoutSuccessHandler(logoutSuccessHandler))
                .headers(configurer -> configurer.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
                .addFilterBefore(jwtAuthenticationTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .userDetailsService(userDetailsService);

        return httpSecurity.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.addAllowedOriginPattern("*");
        corsConfiguration.addAllowedHeader("*");
        corsConfiguration.addAllowedMethod("*");
        corsConfiguration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        return source;
    }

}