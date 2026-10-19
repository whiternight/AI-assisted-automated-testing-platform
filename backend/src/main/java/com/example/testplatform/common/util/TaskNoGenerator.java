package com.example.testplatform.common.util;

import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class TaskNoGenerator {

    private static final AtomicInteger counter = new AtomicInteger(0);
    private static volatile String currentDate = "";

    /**
     * 生成任务流水号
     * 格式：T-yyyyMMdd-{6位序号}
     * 示例：T-20260609-000001
     */
    public String generate() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 日期变化时重置计数器（双重检查锁保证线程安全）
        if (!today.equals(currentDate)) {
            synchronized (this) {
                if (!today.equals(currentDate)) {
                    currentDate = today;
                    counter.set(0);
                }
            }
        }
        int seq = counter.incrementAndGet();
        return String.format("T-%s-%06d", today, seq);
    }
}