package com.fenglin.tenant.service;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * FIN-15：租户开通时同步初始化财务域（账套/科目/会计期间/币种）。
 *
 * <p>财务侧初始化是单事务 + 幂等接口，这里只负责调用与失败告警：
 * 失败时记录 ERROR 日志并抛异常，让租户创建事务整体回滚，避免出现"租户已建、财务不可用"的半成品。</p>
 */
@Component
public class FinanceProvisioner {

    private static final Logger log = LoggerFactory.getLogger(FinanceProvisioner.class);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final String initUrl;
    private final String internalToken;

    public FinanceProvisioner(
            @Value("${fenglin.finance.init-url:http://localhost:18098/api/finance/v1/internal/tenants}")
            String initUrl,
            @Value("${fenglin.internal.token:fenglin-internal-dev}") String internalToken) {
        this.initUrl = initUrl;
        this.internalToken = internalToken;
    }

    public void initialize(long tenantId, String tenantName) {
        String body = "{\"tenant_name\":" + quote(tenantName) + "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(initUrl + "/" + tenantId + "/finance-init"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Internal-Token", internalToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.error("财务域初始化失败 tenant={} status={} body={}", tenantId, response.statusCode(),
                        response.body());
                throw new BizException(CommonErrorCode.INTERNAL_ERROR,
                        "财务域初始化失败，租户未创建（tenant=" + tenantId + "）");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("财务域初始化调用异常 tenant={} url={}", tenantId, initUrl, ex);
            throw new BizException(CommonErrorCode.INTERNAL_ERROR,
                    "财务域初始化失败，租户未创建（tenant=" + tenantId + "）", ex);
        }
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
