package com.fenglin.gateway.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtVerifierTest {

    private static final String ISSUER = "https://hub.test";
    private static RSAKey rsaKey;
    private static JwtVerifier verifier;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("kid-1")
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(rsaKey.toPublicJWK()));
        verifier = new JwtVerifier(source, ISSUER);
    }

    @Test
    void verifiesValidToken() throws Exception {
        String token = sign(claims(Instant.now().plusSeconds(120)));
        JWTClaimsSet result = verifier.verify(token);

        assertThat(result.getLongClaim("tenant_id")).isEqualTo(1001L);
        assertThat(result.getLongClaim("user_id")).isEqualTo(2001L);
        assertThat(result.getStringClaim("token_use")).isEqualTo("access");
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = sign(claims(Instant.now().minusSeconds(60)));
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(BadJOSEException.class);
    }

    @Test
    void rejectsWrongIssuer() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://evil.test")
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .claim("tenant_id", 1001L)
                .build();
        assertThatThrownBy(() -> verifier.verify(sign(claims))).isInstanceOf(BadJOSEException.class);
    }

    @Test
    void rejectsTamperedSignature() throws Exception {
        String token = sign(claims(Instant.now().plusSeconds(60)));
        String tampered = token.substring(0, token.length() - 3) + "abc";
        assertThatThrownBy(() -> verifier.verify(tampered)).isInstanceOf(BadJOSEException.class);
    }

    private JWTClaimsSet claims(Instant expiration) {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("2001")
                .expirationTime(Date.from(expiration))
                .claim("token_use", "access")
                .claim("tenant_id", 1001L)
                .claim("user_id", 2001L)
                .build();
    }

    private String sign(JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaKey.getKeyID())
                .build(), claims);
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }
}
