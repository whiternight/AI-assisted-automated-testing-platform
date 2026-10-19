package com.example.testplatform.auth.util;

import java.util.regex.Pattern;

/**
 * 用户输入验证工具类
 *
 * 验证规则：
 * - 用户名：字母开头，允许字母、数字、下划线，长度6-20位
 * - 密码：至少8位，包含大小写字母、数字和特殊字符
 */
public class ValidationUtil {

    // 用户名正则：字母开头，允许字母、数字、下划线，长度6-20
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{5,19}$");

    // 密码正则：至少8位，必须包含大写字母、小写字母、数字、特殊字符
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[\\S]{8,20}$");

    // 禁止的用户名关键词
    private static final String[] FORBIDDEN_USERNAMES = {
            "admin", "administrator", "root", "system", "test", "user",
            "guest", "superuser", "operator", "manager", "master"
    };

    /**
     * 验证用户名格式
     *
     * 规则：
     * 1. 不能为null或空字符串
     * 2. 长度6-20个字符
     * 3. 必须以字母开头
     * 4. 只能包含字母、数字、下划线
     * 5. 不能包含特殊字符（如 @ # $ % ^ & * 等）
     * 6. 不能使用保留关键词（admin、root、system等）
     * 7. 不能连续使用相同字符超过3次（如 aaaa、1111）
     *
     * @param username 待验证的用户名
     * @return 验证结果消息，null表示验证通过
     */
    public static String validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "用户名不能为空";
        }

        // 检查长度
        if (username.length() < 6) {
            return "用户名长度不能少于6个字符";
        }
        if (username.length() > 20) {
            return "用户名长度不能超过20个字符";
        }

        // 检查是否以字母开头
        if (!Character.isLetter(username.charAt(0))) {
            return "用户名必须以字母开头";
        }

        // 检查格式（只允许字母、数字、下划线）
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return "用户名只能包含字母、数字和下划线，且不能包含特殊字符";
        }

        // 检查是否包含连续重复字符（超过3次）
        if (hasConsecutiveRepeatedChars(username, 4)) {
            return "用户名不能包含连续重复的字符（如aaaa、1111）";
        }

        // 检查是否为保留关键词
        if (isForbiddenUsername(username)) {
            return "该用户名被系统保留，请使用其他用户名";
        }

        return null; // 验证通过
    }

    /**
     * 验证密码安全度
     *
     * 规则：
     * 1. 不能为null或空字符串
     * 2. 长度至少8位，最多20位
     * 3. 必须包含至少一个大写字母
     * 4. 必须包含至少一个小写字母
     * 5. 必须包含至少一个数字
     * 6. 必须包含至少一个特殊字符（@$!%*?&）
     * 7. 不能包含空格
     * 8. 不能包含用户名作为子串
     * 9. 不能有连续3个以上相同字符
     * 10. 不能有连续3个以上顺序字符（如abc、123）
     *
     * @param password 待验证的密码
     * @param username 用户名（用于检查密码是否包含用户名）
     * @return 验证结果消息，null表示验证通过
     */
    public static String validatePassword(String password, String username) {
        if (password == null || password.isEmpty()) {
            return "密码不能为空";
        }

        // 检查长度
        if (password.length() < 8) {
            return "密码长度不能少于8个字符";
        }
        if (password.length() > 20) {
            return "密码长度不能超过20个字符";
        }

        // 检查是否包含空格
        if (password.contains(" ")) {
            return "密码不能包含空格";
        }

        // 检查是否包含大写字母
        if (!Pattern.compile("[A-Z]").matcher(password).find()) {
            return "密码必须包含至少一个大写字母";
        }

        // 检查是否包含小写字母
        if (!Pattern.compile("[a-z]").matcher(password).find()) {
            return "密码必须包含至少一个小写字母";
        }

        // 检查是否包含数字
        if (!Pattern.compile("\\d").matcher(password).find()) {
            return "密码必须包含至少一个数字";
        }

        // 检查是否包含特殊字符
        if (!Pattern.compile("[@$!%*?&]").matcher(password).find()) {
            return "密码必须包含至少一个特殊字符（@$!%*?&）";
        }

        // 检查密码是否包含用户名
        if (username != null && username.length() >= 3 &&
                password.toLowerCase().contains(username.toLowerCase())) {
            return "密码不能包含用户名";
        }

        // 检查是否有连续3个以上相同字符
        if (hasConsecutiveRepeatedChars(password, 3)) {
            return "密码不能包含连续3个以上相同字符（如aaa、111）";
        }

        // 检查是否有连续3个以上顺序字符
        if (hasSequentialChars(password, 3)) {
            return "密码不能包含连续3个以上顺序字符（如abc、123）";
        }

        return null; // 验证通过
    }

    /**
     * 检查字符串中是否包含连续重复字符
     *
     * @param str 待检查的字符串
     * @param maxRepeat 最大允许重复次数
     * @return true表示包含超过maxRepeat次的连续重复字符
     */
    private static boolean hasConsecutiveRepeatedChars(String str, int maxRepeat) {
        if (str == null || str.length() < maxRepeat) {
            return false;
        }

        int count = 1;
        for (int i = 1; i < str.length(); i++) {
            if (str.charAt(i) == str.charAt(i - 1)) {
                count++;
                if (count >= maxRepeat) {
                    return true;
                }
            } else {
                count = 1;
            }
        }
        return false;
    }

    /**
     * 检查字符串中是否包含连续顺序字符
     *
     * @param str 待检查的字符串
     * @param minSequence 最小顺序长度
     * @return true表示包含minSequence长度的连续顺序字符
     */
    private static boolean hasSequentialChars(String str, int minSequence) {
        if (str == null || str.length() < minSequence) {
            return false;
        }

        for (int i = 0; i <= str.length() - minSequence; i++) {
            char c1 = str.charAt(i);
            char c2 = str.charAt(i + 1);
            char c3 = str.charAt(i + 2);

            // 检查递增序列（如abc、123）
            if (c2 == c1 + 1 && c3 == c2 + 1) {
                return true;
            }

            // 检查递减序列（如cba、321）
            if (c2 == c1 - 1 && c3 == c2 - 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查用户名是否为保留关键词
     *
     * @param username 待检查的用户名
     * @return true表示是保留关键词
     */
    private static boolean isForbiddenUsername(String username) {
        String lowerUsername = username.toLowerCase();
        for (String forbidden : FORBIDDEN_USERNAMES) {
            if (lowerUsername.equals(forbidden)) {
                return true;
            }
        }
        return false;
    }
}