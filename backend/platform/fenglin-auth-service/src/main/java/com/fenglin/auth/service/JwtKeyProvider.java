package com.fenglin.auth.service;

import com.fenglin.auth.config.AuthProperties;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Date;
import java.util.Map;

/**
 * RS256 签名与 JWKS 发布。
 * P1 本地开发使用启动时生成的 RSA 密钥；生产接入 KMS/密钥文件（P1 待办）。
 */
@Component
public class JwtKeyProvider {

    private final RSAKey rsaKey;

    public JwtKeyProvider(AuthProperties properties) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            this.rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(properties.getKeyId())
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法初始化 RSA 签名密钥", e);
        }
    }

    public String keyId() {
        return rsaKey.getKeyID();
    }

    public String sign(JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(rsaKey.getKeyID())
                    .type(JOSEObjectType.JWT)
                    .build(), claims);
            jwt.sign(new RSASSASigner(rsaKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT 签名失败", e);
        }
    }

    public JWTClaimsSet verify(String token, String expectedUse) {
        JWTClaimsSet claims = verifySignature(token);
        String use = (String) claims.getClaim("token_use");
        if (!expectedUse.equals(use)) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "令牌类型不匹配");
        }
        if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "令牌已过期");
        }
        return claims;
    }

    /** 撤销/登出场景允许解析已过期令牌，只校验签名 */
    public JWTClaimsSet verifySignature(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()))) {
                throw new BizException(CommonErrorCode.UNAUTHORIZED, "令牌签名不合法");
            }
            return jwt.getJWTClaimsSet();
        } catch (ParseException | JOSEException e) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "令牌无法解析");
        }
    }

    public Map<String, Object> jwks() {
        return new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
    }
}
