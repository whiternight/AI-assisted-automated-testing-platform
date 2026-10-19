package com.example.testplatform;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class FixSchema {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://127.0.0.1:3306/test_platform?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
        String user = "root";
        String pass = "change-me";
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement()) {
            // Add missing columns to flow_execution
            String[] sqls = {
                "ALTER TABLE flow_execution ADD COLUMN input_params longtext NULL COMMENT '执行入参JSON' AFTER result_json",
                "ALTER TABLE flow_execution ADD COLUMN output_params longtext NULL COMMENT '执行出参JSON' AFTER input_params",
                "ALTER TABLE flow_execution ADD COLUMN error_msg text NULL COMMENT '错误信息' AFTER output_params"
            };
            for (String sql : sqls) {
                try {
                    stmt.execute(sql);
                    System.out.println("OK: " + sql.substring(0, 60) + "...");
                } catch (Exception e) {
                    if (e.getMessage().contains("Duplicate column")) {
                        System.out.println("SKIP (already exists): " + sql.substring(0, 60) + "...");
                    } else {
                        System.err.println("FAIL: " + e.getMessage());
                    }
                }
            }
            System.out.println("Schema fix completed.");
        }
    }
}
