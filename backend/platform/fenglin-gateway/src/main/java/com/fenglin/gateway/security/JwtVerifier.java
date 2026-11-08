package com.fenglin.gateway.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import java.text.ParseException;
import java.util.Date;

/**
 * 网关本地 JWT 验签：RS256 + JWKS（RemoteJWKSet 按 kid 自动刷新，未命中时回源）。
 */
public class JwtVerifier {

    private final ConfigurableJWTProcessor<SecurityContext> processor;

    public JwtVerifier(JWKSource<SecurityContext> jwkSource, String issuer) {
        DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        jwtProcessor.setJWTClaimsSetVerifier((claims, context) -> {
            if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
                throw new BadJWTException("令牌已过期");
            }
            if (issuer != null && !issuer.equals(claims.getIssuer())) {
                throw new BadJWTException("issuer 不匹配");
            }
        });
        this.processor = jwtProcessor;
    }

    public JWTClaimsSet verify(String token) throws BadJOSEException, ParseException, JOSEException {
        return processor.process(token, null);
    }
}
