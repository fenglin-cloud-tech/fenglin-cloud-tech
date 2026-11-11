package com.fenglin.auth.service;

import com.fenglin.auth.config.AuthProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;

/**
 * 默认密钥来源：JWK Set（环境变量/Secret）&gt; PEM（环境变量/Secret）&gt; 本地密钥文件。
 *
 * <p>本地文件仅用于开发态持久化，路径默认 {@code .keys/jwt-signing-key.json}，已在 .gitignore 中排除。
 * 文件不存在时生成 2048 位 RSA 密钥并落盘，重启后复用，避免每次重启导致全员掉线。
 */
public class DefaultJwtKeySource implements JwtKeySource {

    private static final Logger log = LoggerFactory.getLogger(DefaultJwtKeySource.class);
    private static final String MODE_AUTO = "auto";
    private static final String MODE_JWK = "jwk";
    private static final String MODE_PEM = "pem";
    private static final String MODE_FILE = "file";

    private final AuthProperties properties;

    public DefaultJwtKeySource(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public JWKSet load() {
        AuthProperties.Key key = properties.getKey();
        String mode = key.getSource() == null ? MODE_AUTO : key.getSource().toLowerCase();
        if (MODE_AUTO.equals(mode)) {
            if (notBlank(key.getJwkSet())) {
                mode = MODE_JWK;
            } else if (notBlank(key.getPrivateKeyPem())) {
                mode = MODE_PEM;
            } else {
                mode = MODE_FILE;
            }
        }
        return switch (mode) {
            case MODE_JWK -> loadFromJwkSet(key);
            case MODE_PEM -> loadFromPem(key);
            case MODE_FILE -> loadOrCreateFile(key);
            default -> throw new IllegalStateException("未知的 JWT 密钥来源：" + mode);
        };
    }

    @Override
    public String description() {
        return "default(jwk/pem/file)";
    }

    private JWKSet loadFromJwkSet(AuthProperties.Key key) {
        try {
            JWKSet set = JWKSet.parse(key.getJwkSet());
            requireRsa(set, "JWK Set");
            log.info("已从环境变量/Secret 加载 JWK 密钥集，kids={}", kids(set));
            return set;
        } catch (java.text.ParseException e) {
            throw new IllegalStateException("fenglin.auth.key.jwk-set 不是合法的 JWK Set JSON", e);
        }
    }

    private JWKSet loadFromPem(AuthProperties.Key key) {
        try {
            List<JWK> keys = new ArrayList<>();
            keys.add(normalize(JWK.parseFromPEMEncodedObjects(key.getPrivateKeyPem()), activeKid(key)));
            for (String pem : key.getPublicKeysPem()) {
                if (notBlank(pem)) {
                    keys.add(normalize(JWK.parseFromPEMEncodedObjects(pem), null));
                }
            }
            JWKSet set = new JWKSet(keys);
            log.info("已从环境变量/Secret 加载 PEM 密钥，kids={}", kids(set));
            return set;
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException("fenglin.auth.key.private-key-pem / public-keys-pem 解析失败", e);
        }
    }

    private JWKSet loadOrCreateFile(AuthProperties.Key key) {
        Path path = Paths.get(key.getFile());
        try {
            if (Files.exists(path)) {
                JWKSet set = JWKSet.parse(Files.readString(path, StandardCharsets.UTF_8));
                requireRsa(set, "本地密钥文件 " + path.toAbsolutePath());
                log.info("已加载本地 JWT 签名密钥 {}，kids={}", path.toAbsolutePath(), kids(set));
                return set;
            }
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            RSAKey generated = generate(activeKid(key));
            Files.writeString(path, new JWKSet(generated).toString(false), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            restrictPermissions(path);
            log.info("已生成本地 JWT 签名密钥并写入 {}（kid={}），后续重启将复用", path.toAbsolutePath(),
                    generated.getKeyID());
            return new JWKSet(generated);
        } catch (IOException e) {
            throw new IllegalStateException("本地 JWT 密钥文件读写失败：" + path.toAbsolutePath(), e);
        } catch (java.text.ParseException e) {
            throw new IllegalStateException("本地 JWT 密钥文件不是合法的 JWK Set：" + path.toAbsolutePath(), e);
        }
    }

    private RSAKey generate(String kid) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(kid)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法生成 RSA 签名密钥", e);
        }
    }

    private JWK normalize(JWK jwk, String kid) {
        if (!(jwk instanceof RSAKey rsa)) {
            throw new IllegalStateException("仅支持 RSA/RS256 签名密钥");
        }
        String resolvedKid = kid;
        if (!notBlank(resolvedKid)) {
            resolvedKid = notBlank(rsa.getKeyID()) ? rsa.getKeyID() : thumbprint(rsa);
        }
        return new RSAKey.Builder(rsa)
                .keyID(resolvedKid)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    private String thumbprint(RSAKey rsa) {
        try {
            return "hub-" + rsa.computeThumbprint().toString().substring(0, 16);
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException("无法计算密钥指纹", e);
        }
    }

    private void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            log.warn("当前文件系统不支持 POSIX 权限，请确认 {} 已排除在版本库之外", path.toAbsolutePath());
        }
    }

    private void requireRsa(JWKSet set, String where) {
        boolean hasRsa = set.getKeys().stream().anyMatch(k -> k instanceof RSAKey);
        if (!hasRsa) {
            throw new IllegalStateException(where + " 中没有任何 RSA 密钥");
        }
    }

    private List<String> kids(JWKSet set) {
        return set.getKeys().stream().map(JWK::getKeyID).toList();
    }

    private String activeKid(AuthProperties.Key key) {
        return notBlank(key.getActiveKid()) ? key.getActiveKid() : properties.getKeyId();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
