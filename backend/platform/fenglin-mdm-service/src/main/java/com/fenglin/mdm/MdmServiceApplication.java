package com.fenglin.mdm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 平台统一往来单位主数据（MDM）服务。
 */
@SpringBootApplication
@MapperScan("com.fenglin.mdm.mapper")
public class MdmServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MdmServiceApplication.class, args);
    }
}
