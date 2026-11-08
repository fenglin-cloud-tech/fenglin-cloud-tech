package com.fenglin.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 丰麟云销（CRM）业务域服务入口。使用独立库 fenglin_crm，不复用平台库。
 */
@SpringBootApplication
public class CrmServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmServiceApplication.class, args);
    }
}
