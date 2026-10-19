package com.example.testplatform.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全工具类
 * 用于从 SecurityContext 中获取当前用户信息
 *
 * @author TestPlatform
 */
@Slf4j
public class SecurityUtil {

    /**
     * 获取当前登录用户ID
     *
     * @return 用户ID，未登录返回 null
     */
    public static Long getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("用户未登录");
                return null;
            }

            // 优先从 credentials 中获取用户ID（JwtAuthenticationFilter 存入的）
            Object credentials = authentication.getCredentials();
            if (credentials instanceof String credStr && !credStr.isEmpty()) {
                try {
                    return Long.parseLong(credStr);
                } catch (NumberFormatException e) {
                    log.warn("无法从credentials解析用户ID: {}", credentials);
                }
            }

            // 兼容处理：从 Principal 中获取用户ID
            Object principal = authentication.getPrincipal();

            if (principal instanceof Long) {
                return (Long) principal;
            } else if (principal instanceof String) {
                try {
                    return Long.parseLong((String) principal);
                } catch (NumberFormatException e) {
                    log.warn("无法解析用户ID: {}", principal);
                    return null;
                }
            } else {
                log.warn("未知的用户主体类型: {}", principal.getClass().getName());
                return null;
            }

        } catch (Exception e) {
            log.error("获取当前用户ID失败", e);
            return null;
        }
    }

    /**
     * 获取当前登录用户名
     *
     * @return 用户名，未登录返回 null
     */
    public static String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                return null;
            }

            return authentication.getName();
            
        } catch (Exception e) {
            log.error("获取当前用户名失败", e);
            return null;
        }
    }

    /**
     * 检查当前用户是否已认证
     *
     * @return true=已认证，false=未认证
     */
    public static boolean isAuthenticated() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null && authentication.isAuthenticated();
        } catch (Exception e) {
            log.error("检查认证状态失败", e);
            return false;
        }
    }
}
