
package com.example.testplatform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
public class PasswordTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    public void testAndGeneratePassword() {
        // 数据库中当前的密码
        String dbPassword = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi";

        System.out.println("========== 密码测试 ==========");
        System.out.println("数据库密码: " + dbPassword);
        System.out.println();

        // 测试常见密码
        String[] testPasswords = {"admin123", "123456", "admin", "password", "test123"};
        for (String pwd : testPasswords) {
            boolean matches = passwordEncoder.matches(pwd, dbPassword);
            System.out.println(pwd + " -> " + (matches ? "✅ 匹配" : "❌ 不匹配"));
        }

        System.out.println();
        System.out.println("========== 生成新密码 ==========");

        // 生成新的 BCrypt 密码
        String newAdminPassword = passwordEncoder.encode("admin123");
        System.out.println("admin123 的新密码: " + newAdminPassword);

        String newPassword123 = passwordEncoder.encode("123456");
        System.out.println("123456 的新密码: " + newPassword123);
    }
}