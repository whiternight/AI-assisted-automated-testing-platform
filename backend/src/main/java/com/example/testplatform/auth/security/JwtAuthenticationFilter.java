
package com.example.testplatform.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.example.testplatform.auth.mapper.UserMapper;
import com.example.testplatform.auth.domain.po.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT认证过滤器 - 拦截请求并验证JWT Token
 *
 * 关键点：
 * 1. 继承OncePerRequestFilter确保每个请求只执行一次
 * 2. 从请求头提取Token
 * 3. 验证Token有效性
 * 4. 加载用户信息并设置到SecurityContext
 * 5. 在过滤器链中位于UsernamePasswordAuthenticationFilter之前
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final UserMapper userMapper;

    /**
     * 过滤器核心逻辑
     *
     * 执行流程：
     * 1. 从请求头提取JWT Token
     * 2. 验证Token是否有效
     * 3. 从Token中获取用户名
     * 4. 加载用户详情（包含权限）
     * 5. 构建认证对象并设置到SecurityContext
     * 6. 继续过滤器链
     *
     * 关键点：
     * - 如果Token无效，不会设置认证，后续请求会被拒绝
     * - 不在此处抛出异常，让Spring Security统一处理
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // ========== 步骤1：从请求头提取Token ==========
        String token = getTokenFromRequest(request);

        // ========== 步骤2：验证Token ==========
        // 关键点：Token存在且有效才进行认证
        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {

            // ========== 步骤3：从Token获取用户名 ==========
            String username = jwtUtil.getUsernameFromToken(token);

            // ========== 步骤4：加载用户详情 ==========
            // 关键点：这里会查询数据库获取用户信息和权限
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // ========== 步骤5：构建认证对象 ==========
            // 关键点：
            // - 第一个参数是principal（用户详情）
            // - 第二个参数是credentials（JWT不需要，设为null）
            // - 第三个参数是authorities（用户权限列表）
            // 查询用户ID，存入credentials字段供SecurityUtil使用
            // @TableLogic 启用后，MP 自动过滤 deleted=1
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, username)
                    .select(User::getId));
            String userIdStr = (user != null) ? String.valueOf(user.getId()) : null;

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,           // principal
                            userIdStr,             // credentials：存放用户ID
                            userDetails.getAuthorities()  // authorities
                    );

            // 设置请求详情（IP地址、Session ID等）
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            // ========== 步骤6：设置到SecurityContext ==========
            // 关键点：设置后，后续的@PreAuthorize等注解才能获取用户信息
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // ========== 步骤7：继续过滤器链 ==========
        // 无论是否有Token，都继续执行后续过滤器
        // 如果没有Token或Token无效，SecurityContext为空，后续会被拒绝访问
        filterChain.doFilter(request, response);
    }

    /**
     * 从HTTP请求头中提取JWT Token
     *
     * 期望格式：Authorization: Bearer <token>
     *
     * 关键点：
     * - 检查Authorization头是否存在
     * - 检查是否以"Bearer "开头
     * - 截取"Bearer "后面的部分作为Token
     *
     * @param request HTTP请求
     * @return JWT Token字符串，如果没有则返回null
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        // 检查Token格式：Bearer <token>
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            // 截取"Bearer "后面的部分（索引7开始）
            return bearerToken.substring(7);
        }
        return null;
    }
}