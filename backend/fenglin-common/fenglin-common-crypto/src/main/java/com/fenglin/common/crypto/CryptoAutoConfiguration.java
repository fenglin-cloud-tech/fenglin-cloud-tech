package com.fenglin.common.crypto;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Base64;

@AutoConfiguration
@EnableConfigurationProperties(FenglinCryptoProperties.class)
@ConditionalOnProperty(prefix = "fenglin.crypto", name = "data-key-base64")
public class CryptoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KmsKeyProvider staticKmsKeyProvider(FenglinCryptoProperties properties) {
        byte[] key = Base64.getDecoder().decode(properties.getDataKeyBase64());
        return () -> key;
    }

    @Bean
    @ConditionalOnMissingBean
    public AesGcmCipher aesGcmCipher(KmsKeyProvider keyProvider) {
        return new AesGcmCipher(keyProvider.currentDataKey());
    }

    @Bean
    @ConditionalOnMissingBean
    public PiiHasher piiHasher() {
        return new PiiHasher();
    }
}
