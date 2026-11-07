package com.fenglin.common.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fenglin.crypto")
public class FenglinCryptoProperties {

    /**
     * 本地/测试用数据密钥（Base64，32 字节）。生产环境由 KMS 提供实现，禁止写入仓库。
     */
    private String dataKeyBase64;

    public String getDataKeyBase64() {
        return dataKeyBase64;
    }

    public void setDataKeyBase64(String dataKeyBase64) {
        this.dataKeyBase64 = dataKeyBase64;
    }
}
