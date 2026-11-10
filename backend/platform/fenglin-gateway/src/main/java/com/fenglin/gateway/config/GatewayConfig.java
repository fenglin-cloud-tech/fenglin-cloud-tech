package com.fenglin.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.gateway.security.JwtVerifier;
import com.fenglin.gateway.security.PermissionResolver;
import com.fenglin.gateway.security.RevocationChecker;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.MalformedURLException;
import java.net.URL;

@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayConfig {

    @Bean
    public JwtVerifier jwtVerifier(GatewayProperties properties) {
        try {
            URL jwks = new URL(properties.getJwksUri());
            JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(jwks);
            return new JwtVerifier(jwkSource, properties.getIssuer());
        } catch (MalformedURLException e) {
            throw new IllegalStateException("网关 JWKS 地址不合法: " + properties.getJwksUri(), e);
        }
    }

    @Bean
    public RevocationChecker revocationChecker(GatewayProperties properties, WebClient.Builder webClientBuilder) {
        WebClient webClient = webClientBuilder
                .baseUrl(properties.getRevocation().getAuthInternalUri())
                .build();
        return new RevocationChecker(properties, webClient);
    }

    @Bean
    public PermissionResolver permissionResolver(GatewayProperties properties, WebClient.Builder webClientBuilder,
                                                 ObjectProvider<ReactiveStringRedisTemplate> redisProvider,
                                                 ObjectMapper objectMapper) {
        WebClient webClient = webClientBuilder
                .baseUrl(properties.getPermissionCache().getAuthInternalUri())
                .build();
        return new PermissionResolver(properties, webClient, redisProvider.getIfAvailable(), objectMapper);
    }
}
