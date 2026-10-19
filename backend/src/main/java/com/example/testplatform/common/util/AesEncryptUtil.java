package com.example.testplatform.common.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES/CBC/PKCS5Padding 加解密工具类
 * 密钥长度：256位
 * IV：每次加密随机生成，并拼接在密文前面（前16字节）
 * 最终输出格式：Base64(IV + 密文)
 */
@Slf4j
@Component
public class AesEncryptUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int IV_SIZE = 16;          // CBC模式IV长度固定为16字节
    private static final int KEY_BIT_LEN = 256;     // 256位密钥 = 32字节
    private static final String DEFAULT_KEY_PATTERN = "change-me";

    @Value("${aes.key}")
    private String aesKey;

    private SecretKeySpec secretKeySpec;            // 缓存由配置密钥生成的SecretKey

    /**
     * 初始化：将注入的密钥字符串转换为 SecretKeySpec
     * 转换规则：
     *   - 若密钥字符串UTF-8字节长度为32，直接使用
     *   - 否则通过SHA-256哈希得到32字节密钥
     */
    @PostConstruct
    public void init() {
        if (aesKey == null || aesKey.isEmpty()) {
            throw new IllegalStateException("aes.key 不能为空");
        }
        if (aesKey.contains(DEFAULT_KEY_PATTERN)) {
            log.warn("【安全警告】AES 密钥使用默认值，生产环境请通过环境变量 AES_KEY 设置强密钥！");
        }
        this.secretKeySpec = deriveSecretKey(aesKey);
    }

    // ==================== 实例方法（使用注入的密钥） ====================

    /**
     * 加密（使用配置的密钥）
     * @param plainText 明文（非空）
     * @return Base64(IV + 密文)
     */
    public String encrypt(String plainText) {
        if (plainText == null) {
            throw new IllegalArgumentException("明文不能为null");
        }
        byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);
        byte[] iv = generateRandomIv();
        byte[] cipherBytes = doCipher(plainBytes, Cipher.ENCRYPT_MODE, secretKeySpec, iv);
        byte[] combined = new byte[IV_SIZE + cipherBytes.length];
        System.arraycopy(iv, 0, combined, 0, IV_SIZE);
        System.arraycopy(cipherBytes, 0, combined, IV_SIZE, cipherBytes.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * 解密（使用配置的密钥）
     * @param cipherText Base64(IV + 密文)
     * @return 明文
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            throw new IllegalArgumentException("密文不能为空");
        }
        byte[] combined = Base64.getDecoder().decode(cipherText);
        if (combined.length < IV_SIZE) {
            throw new IllegalArgumentException("无效的密文格式");
        }
        byte[] iv = new byte[IV_SIZE];
        byte[] encrypted = new byte[combined.length - IV_SIZE];
        System.arraycopy(combined, 0, iv, 0, IV_SIZE);
        System.arraycopy(combined, IV_SIZE, encrypted, 0, encrypted.length);
        byte[] plainBytes = doCipher(encrypted, Cipher.DECRYPT_MODE, secretKeySpec, iv);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    // ==================== 静态方法（使用传入的密钥） ====================

    /**
     * 加密（使用指定的密钥）
     * @param plainText 明文
     * @param key       密钥字符串（长度任意，内部会转换为256位密钥）
     * @return Base64(IV + 密文)
     */
    public static String encrypt(String plainText, String key) {
        if (plainText == null || key == null) {
            throw new IllegalArgumentException("明文和密钥不能为null");
        }
        SecretKeySpec secretKey = deriveSecretKey(key);
        byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);
        byte[] iv = generateRandomIv();
        byte[] cipherBytes = doCipher(plainBytes, Cipher.ENCRYPT_MODE, secretKey, iv);
        byte[] combined = new byte[IV_SIZE + cipherBytes.length];
        System.arraycopy(iv, 0, combined, 0, IV_SIZE);
        System.arraycopy(cipherBytes, 0, combined, IV_SIZE, cipherBytes.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * 解密（使用指定的密钥）
     * @param cipherText Base64(IV + 密文)
     * @param key        密钥字符串
     * @return 明文
     */
    public static String decrypt(String cipherText, String key) {
        if (cipherText == null || cipherText.isEmpty() || key == null) {
            throw new IllegalArgumentException("密文和密钥不能为空");
        }
        SecretKeySpec secretKey = deriveSecretKey(key);
        byte[] combined = Base64.getDecoder().decode(cipherText);
        if (combined.length < IV_SIZE) {
            throw new IllegalArgumentException("无效的密文格式");
        }
        byte[] iv = new byte[IV_SIZE];
        byte[] encrypted = new byte[combined.length - IV_SIZE];
        System.arraycopy(combined, 0, iv, 0, IV_SIZE);
        System.arraycopy(combined, IV_SIZE, encrypted, 0, encrypted.length);
        byte[] plainBytes = doCipher(encrypted, Cipher.DECRYPT_MODE, secretKey, iv);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 根据任意密钥字符串派生出256位AES密钥
     * 规则：若UTF-8字节长度正好为32则直接使用，否则计算SHA-256哈希
     */
    private static SecretKeySpec deriveSecretKey(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != KEY_BIT_LEN / 8) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                keyBytes = md.digest(keyBytes);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA-256算法不可用", e);
            }
        }
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * 生成随机IV（16字节）
     */
    private static byte[] generateRandomIv() {
        byte[] iv = new byte[IV_SIZE];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        return iv;
    }

    /**
     * 执行AES/CBC加解密
     * @param input      明文或密文字节
     * @param mode       Cipher.ENCRYPT_MODE 或 Cipher.DECRYPT_MODE
     * @param secretKey  AES密钥
     * @param iv         初始化向量
     * @return 加密后或解密后的字节数组
     */
    private static byte[] doCipher(byte[] input, int mode, SecretKeySpec secretKey, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(mode, secretKey, ivSpec);
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new RuntimeException("AES加解密失败", e);
        }
    }
}