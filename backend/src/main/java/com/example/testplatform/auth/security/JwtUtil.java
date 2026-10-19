
package com.example.testplatform.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类 - 负责Token的生成、解析和验证
 *
 * 关键点：
 * 1. 使用HMAC-SHA256算法签名
 * 2. Token包含用户ID、用户名、角色信息
 * 3. 支持Token有效期配置
 */
@Slf4j
@Component
public class JwtUtil {

    /**
     * JWT签名密钥 - 从配置文件读取
     * 生产环境建议使用更复杂的密钥并存储在环境变量中
     */
    @Value("${jwt.secret:change-me-jwt-secret-key}")
    private String secret;

    /**
     * Token过期时间（毫秒）- 默认24小时
     * 86400000ms = 24h
     */
    @Value("${jwt.expiration:86400000}")
    private Long expiration;

    @PostConstruct
    public void init() {
        if (secret.contains("TestPlatformSecretKey")) {
            log.warn("【安全警告】JWT 密钥使用默认/硬编码值，生产环境请配置独立的强密钥！");
        }
    }

    /**
     * 获取签名密钥
     * 关键点：将字符串密钥转换为安全的SecretKey对象
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成JWT Token
     *
     * 关键点：
     * 1. Claims中存储userId和roles（自定义数据）
     * 2. subject设置为用户名（标准字段）
     * 3. 设置签发时间和过期时间
     *
     * @param username 用户名（作为subject）
     * @param userId 用户ID（存入claims）
     * @param roles 用户角色（逗号分隔，存入claims）
     * @return JWT Token字符串
     */
    public String generateToken(String username, Long userId, String roles) {
        Map<String, Object> claims = new HashMap<>();
        // 将用户ID和角色信息放入claims（自定义载荷）
        claims.put("userId", userId);
        claims.put("roles", roles);

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        // 构建JWT Token
        return Jwts.builder()
                .claims(claims)              // 自定义claims
                .subject(username)           // 主题（用户名）
                .issuedAt(now)               // 签发时间
                .expiration(expiryDate)      // 过期时间
                .signWith(getSigningKey())   // 签名
                .compact();                  // 压缩为字符串
    }

    /**
     * 从Token中获取用户名
     * 关键点：解析subject字段
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())  // 验证签名
                .build()
                .parseSignedClaims(token)      // 解析Token
                .getPayload();                 // 获取载荷
        return claims.getSubject();            // 返回subject（用户名）
    }

    /**
     * 从Token中获取用户ID
     * 关键点：从自定义claims中提取userId
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("userId", Long.class);
    }

    /**
     * 从Token中获取角色信息
     * 关键点：从自定义claims中提取roles
     */
    public String getRolesFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("roles", String.class);
    }

    /**
     * 验证Token是否有效
     *
     * 关键点：
     * 1. 验证签名是否正确
     * 2. 验证是否过期
     * 3. 验证格式是否正确
     *
     * @param token JWT Token
     * @return true=有效，false=无效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            // Token无效、过期、被篡改等情况都会抛出异常
            return false;
        }
    }
}