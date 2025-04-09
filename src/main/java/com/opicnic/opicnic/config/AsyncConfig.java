package com.opicnic.opicnic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 비동기 처리를 위한 설정 클래스.
 * ExecutorService 를 사용하여 비동기 작업 처리함.
 */
@Configuration
public class AsyncConfig {

    @Bean
    public ExecutorService taskExecutor() {
        return Executors.newFixedThreadPool(4); // 필요에 따라 조절 (코어 수 등)
    }
}