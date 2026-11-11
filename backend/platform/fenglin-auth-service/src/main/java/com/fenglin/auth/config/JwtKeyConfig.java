package com.fenglin.auth.config;

import com.fenglin.auth.service.DefaultJwtKeySource;
import com.fenglin.auth.service.JwtKeySource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 密钥来源装配：默认走 JWK/PEM/文件；接 KMS 时新增实现并把 provider 指向它即可。
 */
@Configuration
public class JwtKeyConfig {

    @Bean
    @ConditionalOnProperty(prefix = "fenglin.auth.key", name = "provider", havingValue = "default",
            matchIfMissing = true)
    public JwtKeySource jwtKeySource(AuthProperties properties) {
        return new DefaultJwtKeySource(properties);
    }
}
