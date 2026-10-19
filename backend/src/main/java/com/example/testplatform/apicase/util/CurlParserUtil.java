package com.example.testplatform.apicase.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * cURL 命令解析工具类
 * <p>
 * 支持解析标准 cURL 命令，提取 HTTP 方法、URL、请求头、请求体、URL 参数、认证信息等。
 */
@Slf4j
public class CurlParserUtil {

    @Data
    public static class ParseResult {
        private String method = "GET";
        private String url;
        private Map<String, String> headers = new LinkedHashMap<>();
        private String body;
        /** 从 URL 查询字符串解析出的参数 */
        private Map<String, String> queryParams = new LinkedHashMap<>();
        /** 是否跟随重定向 */
        private boolean followRedirects = false;
        /** 超时时间（秒），-1 表示未指定 */
        private int maxTime = -1;
    }

    /**
     * 解析 cURL 命令字符串
     *
     * @param curlCommand cURL 命令
     * @return 解析结果
     */
    public static ParseResult parse(String curlCommand) {
        if (curlCommand == null || curlCommand.trim().isEmpty()) {
            throw new IllegalArgumentException("cURL命令不能为空");
        }

        // 标准化：合并行续行符
        String normalized = curlCommand
                .replace("\\\r\n", " ")
                .replace("\\\n", " ")
                .replace("\\\r", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // 去掉 curl 前缀
        if (normalized.toLowerCase().startsWith("curl ")) {
            normalized = normalized.substring(5).trim();
        } else if (normalized.toLowerCase().equals("curl")) {
            throw new IllegalArgumentException("cURL命令不完整");
        } else {
            throw new IllegalArgumentException("无效的cURL命令，必须以 curl 开头");
        }

        List<String> tokens = tokenize(normalized);
        ParseResult result = new ParseResult();
        boolean methodExplicit = false;

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);

            if (("-X".equals(token) || "--request".equals(token)) && i + 1 < tokens.size()) {
                result.setMethod(tokens.get(++i).toUpperCase());
                methodExplicit = true;
            } else if (("-H".equals(token) || "--header".equals(token)) && i + 1 < tokens.size()) {
                String headerStr = unquote(tokens.get(++i));
                int colonIdx = headerStr.indexOf(':');
                if (colonIdx > 0) {
                    String key = headerStr.substring(0, colonIdx).trim();
                    String value = headerStr.substring(colonIdx + 1).trim();
                    result.getHeaders().put(key, value);
                }
            } else if (isDataFlag(token) && i + 1 < tokens.size()) {
                result.setBody(unquote(tokens.get(++i)));
            } else if (("-u".equals(token) || "--user".equals(token)) && i + 1 < tokens.size()) {
                // Basic Auth: username:password -> Authorization: Basic base64
                String cred = unquote(tokens.get(++i));
                String encoded = Base64.getEncoder().encodeToString(cred.getBytes(StandardCharsets.UTF_8));
                result.getHeaders().put("Authorization", "Basic " + encoded);
            } else if ("-L".equals(token) || "--location".equals(token)) {
                result.setFollowRedirects(true);
            } else if (("--max-time".equals(token) || "--connect-timeout".equals(token)
                    || "-m".equals(token)) && i + 1 < tokens.size()) {
                try {
                    result.setMaxTime((int) Double.parseDouble(unquote(tokens.get(++i))));
                } catch (NumberFormatException e) {
                    log.warn("解析cURL超时参数失败: {}", token);
                }
            } else if (token.startsWith("-") && !isUrlToken(token)) {
                // 跳过未知 flag（如果有值参数则跳过下一个 token）
                if (i + 1 < tokens.size() && !tokens.get(i + 1).startsWith("-")
                        && !isUrlToken(tokens.get(i + 1))) {
                    // 某些 flag 带参数，这里保守跳过
                }
            } else if (!token.startsWith("-")) {
                // 视为 URL
                String url = unquote(token);
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    continue;
                }
                result.setUrl(url);
            }
        }

        // 如果没有显式指定方法，但有 body，默认 POST
        if (!methodExplicit && result.getBody() != null && !result.getBody().isEmpty()) {
            result.setMethod("POST");
        }

        if (result.getUrl() == null || result.getUrl().isEmpty()) {
            throw new IllegalArgumentException("cURL命令中未找到有效的URL");
        }

        // 从 URL 中提取 query parameters
        extractQueryParams(result);

        return result;
    }

    /**
     * 从 URL 中提取查询参数到 queryParams，同时从 url 中去掉 query string
     */
    private static void extractQueryParams(ParseResult result) {
        String url = result.getUrl();
        if (url == null || url.isEmpty()) {
            return;
        }
        try {
            URL parsed = new URL(url);
            String query = parsed.getQuery();
            if (query == null || query.isEmpty()) {
                return;
            }
            Map<String, String> params = new LinkedHashMap<>();
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf('=');
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    params.put(key, value);
                } else if (!pair.isEmpty()) {
                    params.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
                }
            }
            result.setQueryParams(params);
            // 去掉 query string 后的纯净 URL
            String cleanUrl = url;
            int qIdx = url.indexOf('?');
            if (qIdx >= 0) {
                cleanUrl = url.substring(0, qIdx);
            }
            result.setUrl(cleanUrl);
        } catch (Exception e) {
            log.warn("从URL提取查询参数失败: {}", url, e);
        }
    }

    /**
     * 判断是否为数据标志
     */
    private static boolean isDataFlag(String token) {
        return "-d".equals(token) || "--data".equals(token)
                || "--data-raw".equals(token) || "--data-binary".equals(token)
                || "--data-urlencode".equals(token) || "--data-ascii".equals(token);
    }

    /**
     * 判断 token 是否看起来像 URL
     */
    private static boolean isUrlToken(String token) {
        String t = unquote(token);
        return t.startsWith("http://") || t.startsWith("https://");
    }

    /**
     * 去除字符串两端的引号（单引号或双引号）
     */
    private static String unquote(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    /**
     * 将 cURL 命令拆分为 token 列表，正确处理引号内的空格
     */
    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\'' && !inDouble) {
                current.append(c);
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                current.append(c);
                inDouble = !inDouble;
            } else if (c == ' ' && !inSingle && !inDouble) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }
}
