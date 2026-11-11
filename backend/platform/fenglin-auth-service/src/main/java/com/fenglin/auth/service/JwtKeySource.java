package com.fenglin.auth.service;

import com.nimbusds.jose.jwk.JWKSet;

/**
 * JWT 签名密钥来源。
 *
 * <p>默认实现从环境变量/Secret 注入的 JWK、PEM，或本地密钥文件读取；
 * 生产接 KMS 时新增一个实现并把 {@code fenglin.auth.key.provider} 指向它即可，
 * 签发与验签逻辑（{@link JwtKeyProvider}）无需改动。
 */
public interface JwtKeySource {

    JWKSet load();

    String description();
}
