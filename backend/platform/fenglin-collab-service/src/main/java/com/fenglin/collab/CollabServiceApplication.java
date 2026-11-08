package com.fenglin.collab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 丰麟云协（Collaborate）业务域服务入口。使用独立库 fenglin_collab，不复用平台库。
 */
@SpringBootApplication
public class CollabServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollabServiceApplication.class, args);
    }
}
