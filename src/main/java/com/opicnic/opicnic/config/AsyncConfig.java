package com.opicnic.opicnic.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 비동기 처리를 위한 설정 클래스.
 * ExecutorService 를 사용하여 비동기 작업 처리함.
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public ExecutorService taskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
