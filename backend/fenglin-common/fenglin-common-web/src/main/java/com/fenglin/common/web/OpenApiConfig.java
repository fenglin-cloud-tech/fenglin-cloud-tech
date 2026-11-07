package com.fenglin.common.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(OpenAPI.class)
public class OpenApiConfig {

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI fenglinOpenApi() {
        return new OpenAPI().info(new Info()
                .title("丰麟云枢平台 API")
                .version("v1")
                .description("统一响应、统一错误码与租户上下文由平台基座提供"));
    }
}
