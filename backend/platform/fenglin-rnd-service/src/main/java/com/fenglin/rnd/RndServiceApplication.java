package com.fenglin.rnd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 丰麟云研（DevOps）业务域服务入口。使用独立库 fenglin_devops，不复用平台库。
 */
@SpringBootApplication
public class RndServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RndServiceApplication.class, args);
    }
}
