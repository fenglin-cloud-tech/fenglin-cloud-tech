package com.fenglin.intelligence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 丰麟云智（Intelligence）业务域服务入口。
 * 使用独立库 fenglin_intelligence；模型调用、护栏、计量、提示词与能力授权委托平台 AI 中台（hub_ai_*）。
 */
@SpringBootApplication
public class IntelligenceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntelligenceServiceApplication.class, args);
    }
}
