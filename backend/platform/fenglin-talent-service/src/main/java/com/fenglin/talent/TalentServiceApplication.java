package com.fenglin.talent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 丰麟云才（Talent）业务域服务入口。使用独立库 fenglin_talent，不复用平台库。
 */
@SpringBootApplication
public class TalentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TalentServiceApplication.class, args);
    }
}
