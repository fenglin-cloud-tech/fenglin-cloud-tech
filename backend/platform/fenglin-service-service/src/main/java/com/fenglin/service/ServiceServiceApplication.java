package com.fenglin.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 丰麟云服（Service）业务域服务入口。使用独立库 fenglin_service，不复用平台库。
 */
@SpringBootApplication
public class ServiceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceServiceApplication.class, args);
    }
}
