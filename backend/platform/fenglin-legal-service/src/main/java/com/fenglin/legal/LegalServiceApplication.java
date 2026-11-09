package com.fenglin.legal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 丰麟云法（Legal）业务域服务入口。使用独立库 fenglin_legal，不复用平台库。
 */
@SpringBootApplication
public class LegalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegalServiceApplication.class, args);
    }
}
