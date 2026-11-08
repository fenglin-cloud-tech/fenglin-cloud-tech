package com.fenglin.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AiAsyncConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService aiStreamExecutor() {
        return Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "ai-rag-stream");
            thread.setDaemon(true);
            return thread;
        });
    }
}
