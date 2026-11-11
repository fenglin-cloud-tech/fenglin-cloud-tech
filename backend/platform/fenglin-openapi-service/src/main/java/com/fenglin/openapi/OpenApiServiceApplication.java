package com.fenglin.openapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OpenApiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenApiServiceApplication.class, args);
    }
}
