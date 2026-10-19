package com.example.testplatform.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security核心配置类
 * <p>
 * 关键点：
 * 1. 禁用CSRF（因为使用JWT无状态认证）
 * 2. 禁用Session（STATELESS策略）
 * 3. 配置公开接口和需要认证的接口
 * 4. 添加JWT过滤器到过滤器链
 * 5. 配置CORS跨域支持
 */
@Configuration
@EnableWebSecurity          // 启用Spring Security
@EnableMethodSecurity       // 启用方法级权限控制（@PreAuthorize等）
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;

    /**
     * 配置安全过滤链 - 核心配置
     * <p>
     * 关键点：
     * 1. CORS配置
     * 2. CSRF禁用（JWT不需要CSRF保护）
     * 3. Session策略设为STATELESS（无状态）
     * 4. 定义哪些接口需要认证，哪些可以公开访问
     * 5. 注册AuthenticationProvider
     * 6. 添加JWT过滤器在用户名密码过滤器之前
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ========== CORS跨域配置 ==========
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ========== CSRF禁用 ==========
                // 关键点：JWT是无状态的，每个请求都携带token，不需要CSRF保护
                .csrf(csrf -> csrf.disable())

                // ========== Session管理 ==========
                // 关键点：STATELESS表示不创建Session，每次请求都需要携带JWT
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // ========== 授权规则 ==========
                .authorizeHttpRequests(auth -> auth
                        // 公开接口：登录、注册不需要认证
                        .requestMatchers("/auth/**").permitAll()
                        // 直连测试接口：无需登录，即时调试
                        .requestMatchers("/test/direct/**").permitAll()
                        // 列表接口：无需登录，临时调试
                        .requestMatchers("/role/list").permitAll()
                        .requestMatchers("/department/list").permitAll()
                        // Swagger文档：开发环境公开
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Actuator监控：根据需求决定是否公开
                        .requestMatchers("/actuator/**").permitAll()
                        // CRUD接口：需要登录认证
                        .requestMatchers("/api-case/**").authenticated()
                        .requestMatchers("/api-case-change-log/**").authenticated()
                        // 其他所有接口都需要认证
                        .anyRequest().authenticated()
                )

                // ========== 认证提供者 ==========
                .authenticationProvider(authenticationProvider())

                // ========== JWT过滤器 ==========
                // 关键点：将JWT过滤器添加到用户名密码过滤器之前
                // 这样可以在到达认证端点之前就验证Token
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 配置认证提供者
     * <p>
     * 关键点：
     * 1. 使用DaoAuthenticationProvider（基于数据库的认证）
     * 2. 设置UserDetailsService加载用户信息
     * 3. 设置PasswordEncoder加密密码
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        // 设置自定义的UserDetailsService（从数据库加载用户）
        provider.setUserDetailsService(userDetailsService);
        // 设置密码编码器（BCrypt加密）
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * 配置认证管理器
     * 关键点：用于手动触发认证（如在登录接口中）
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 配置密码编码器
     * <p>
     * 关键点：
     * 1. 使用BCrypt强哈希函数
     * 2. 自动加盐，防止彩虹表攻击
     * 3. 每次加密结果不同，但验证结果一致
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS跨域配置
     * <p>
     * 关键点：
     * 1. 允许所有源（生产环境应指定具体域名）
     * 2. 允许常用HTTP方法
     * 3. 允许所有请求头（包括Authorization）
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许的源（生产环境建议改为具体域名）
        configuration.setAllowedOrigins(List.of("*"));
        // 允许的HTTP方法
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE","PATCH", "OPTIONS","HEAD","TRACE"));
        // 允许的请求头（必须包含Authorization才能携带JWT）
        configuration.setAllowedHeaders(Arrays.asList("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}