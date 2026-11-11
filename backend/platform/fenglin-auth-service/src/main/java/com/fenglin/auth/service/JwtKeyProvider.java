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
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RS256 签名与 JWKS 发布。
 *
 * <p>支持同时挂多把公钥：活跃 kid 负责签发，其余 kid 在兼容期内继续验签；
 * JWKS 发布全部公钥，网关/其他服务按 kid 取键。密钥来源由 {@link JwtKeySource} 决定。
 */
@Component
public class JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);

    private final List<RSAKey> keys;
    private final Map<String, RSAKey> keysByKid;
    private final RSAKey activeKey;

    public JwtKeyProvider(JwtKeySource keySource, AuthProperties properties) {
        JWKSet set = keySource.load();
        List<RSAKey> loaded = new ArrayList<>();
        for (JWK jwk : set.getKeys()) {
            if (jwk instanceof RSAKey rsa) {
                loaded.add(rsa);
            }
        }
        if (loaded.isEmpty()) {
            throw new IllegalStateException("未配置任何 RSA 签名密钥");
        }
        String activeKid = properties.getKey() != null && notBlank(properties.getKey().getActiveKid())
                ? properties.getKey().getActiveKid()
                : properties.getKeyId();
        RSAKey chosen = loaded.stream()
                .filter(k -> activeKid.equals(k.getKeyID()))
                .findFirst()
                .orElseGet(() -> loaded.stream()
                        .filter(RSAKey::isPrivate)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("未找到可用私钥，activeKid=" + activeKid)));
        if (!chosen.isPrivate()) {
            throw new IllegalStateException("活跃密钥必须是私钥，kid=" + chosen.getKeyID());
        }
        if (!activeKid.equals(chosen.getKeyID())) {
            log.warn("activeKid={} 未命中，回退到私钥 kid={}", activeKid, chosen.getKeyID());
        }
        this.activeKey = chosen;
        this.keys = List.copyOf(loaded);
        Map<String, RSAKey> indexed = new HashMap<>();
        for (RSAKey key : loaded) {
            if (notBlank(key.getKeyID())) {
                indexed.put(key.getKeyID(), key);
            }
        }
        this.keysByKid = Map.copyOf(indexed);
        log.info("JWT 签名密钥就绪：activeKid={}，已挂载 kids={}", activeKey.getKeyID(), keysByKid.keySet());
    }

    public String keyId() {
        return activeKey.getKeyID();
    }

    public String sign(JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(activeKey.getKeyID())
                    .type(JOSEObjectType.JWT)
                    .build(), claims);
            jwt.sign(new RSASSASigner(activeKey));
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
            String kid = jwt.getHeader().getKeyID();
            RSAKey key = kid == null ? null : keysByKid.get(kid);
            boolean verified = key != null
                    ? verifyWith(jwt, key)
                    : keys.stream().anyMatch(candidate -> verifyWith(jwt, candidate));
            if (!verified) {
                throw new BizException(CommonErrorCode.UNAUTHORIZED, "令牌签名不合法");
            }
            return jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "令牌无法解析");
        }
    }

    public Map<String, Object> jwks() {
        List<JWK> publicKeys = keys.stream().map(key -> (JWK) key.toPublicJWK()).toList();
        return new JWKSet(publicKeys).toJSONObject();
    }

    private boolean verifyWith(SignedJWT jwt, RSAKey key) {
        try {
            return jwt.verify(new RSASSAVerifier(key.toRSAPublicKey()));
        } catch (JOSEException e) {
            return false;
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
