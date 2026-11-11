package com.fenglin.auth.service;

import com.fenglin.auth.config.AuthProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PLAT-15：密钥落盘复用、kid 轮换与多公钥验签。
 */
class JwtKeyRotationTest {

    @TempDir
    Path tempDir;

    @Test
    void fileKeySurvivesRestart() throws Exception {
        Path file = tempDir.resolve(".keys/jwt-signing-key.json");
        AuthProperties firstProperties = properties(file, "k1");
        JwtKeyProvider first = new JwtKeyProvider(new DefaultJwtKeySource(firstProperties), firstProperties);
        String token = first.sign(claims());

        AuthProperties restartedProperties = properties(file, "k1");
        JwtKeyProvider restarted = new JwtKeyProvider(
                new DefaultJwtKeySource(restartedProperties), restartedProperties);

        assertThat(restarted.keyId()).isEqualTo("k1");
        assertThat(restarted.verifySignature(token).getSubject()).isEqualTo("1");
    }

    @Test
    void rotatedProviderVerifiesOldKidAndSignsWithNewKid() throws Exception {
        RSAKey oldKey = generate("k-old");
        RSAKey newKey = generate("k-new");

        Path oldFile = tempDir.resolve("old.json");
        Files.writeString(oldFile, new JWKSet(oldKey).toString(false));
        AuthProperties oldProperties = properties(oldFile, "k-old");
        JwtKeyProvider oldProvider = new JwtKeyProvider(
                new DefaultJwtKeySource(oldProperties), oldProperties);
        String oldToken = oldProvider.sign(claims());

        Path rotateFile = tempDir.resolve("rotate.json");
        Files.writeString(rotateFile, new JWKSet(List.of(oldKey.toPublicJWK(), newKey)).toString(false));
        AuthProperties rotateProperties = properties(rotateFile, "k-new");
        JwtKeyProvider rotated = new JwtKeyProvider(
                new DefaultJwtKeySource(rotateProperties), rotateProperties);

        String newToken = rotated.sign(claims());

        assertThat(rotated.keyId()).isEqualTo("k-new");
        assertThat(SignedJWT.parse(newToken).getHeader().getKeyID()).isEqualTo("k-new");
        assertThat(rotated.verifySignature(oldToken).getSubject()).isEqualTo("1");
        assertThat(rotated.verifySignature(newToken).getSubject()).isEqualTo("1");
        assertThat(rotated.jwks().toString()).contains("k-old").contains("k-new");
    }

    private AuthProperties properties(Path file, String activeKid) {
        AuthProperties properties = new AuthProperties();
        properties.getKey().setSource("file");
        properties.getKey().setFile(file.toString());
        properties.getKey().setActiveKid(activeKid);
        return properties;
    }

    private JWTClaimsSet claims() {
        return new JWTClaimsSet.Builder()
                .subject("1")
                .issuer("test")
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .build();
    }

    private RSAKey generate(String kid) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(kid)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }
}
