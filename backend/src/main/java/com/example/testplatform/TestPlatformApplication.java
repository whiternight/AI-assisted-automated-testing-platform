package com.example.testplatform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan({"com.example.testplatform.apicase.mapper",
        "com.example.testplatform.execution.mapper",
        "com.example.testplatform.app.mapper",
        "com.example.testplatform.auth.mapper",
        "com.example.testplatform.originalservice.mapper",
        "com.example.testplatform.workflow.mapper",
        "com.example.testplatform.testsuite.mapper"})
@EnableScheduling
public class TestPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestPlatformApplication.class, args);
    }

}
