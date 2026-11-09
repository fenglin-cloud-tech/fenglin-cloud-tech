package com.fenglin.intelligence.service;

import com.fenglin.intelligence.client.AiGatewayClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 云智运营/双模式：能力目录与计量只读平台 AI 中台（hub_ai_*），本服务不落库、不计费。
 */
@Service
public class IntelligenceAdminService {

    private final AiGatewayClient gatewayClient;
    private final BiService biService;
    private final String deployMode;
    private final String modelAccessMode;

    public IntelligenceAdminService(AiGatewayClient gatewayClient, BiService biService,
                                    @Value("${fenglin.intelligence.deploy-mode:SAAS}") String deployMode,
                                    @Value("${fenglin.intelligence.model-access-mode:PLATFORM}") String modelAccessMode) {
        this.gatewayClient = gatewayClient;
        this.biService = biService;
        this.deployMode = deployMode;
        this.modelAccessMode = modelAccessMode;
    }

    public Map<String, Object> capabilityGrants(String hostProduct) {
        return gatewayClient.grants(hostProduct);
    }

    public Map<String, Object> usage(String mode, String hostProduct, String from, String to) {
        return gatewayClient.metering(mode, hostProduct, from, to);
    }

    public Map<String, Object> validateMode() {
        return gatewayClient.validateMode();
    }

    public Map<String, Object> modeSummary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deploy_mode", deployMode);
        result.put("model_access_mode", modelAccessMode);
        result.put("supported_deploy_modes", new String[]{"SAAS", "PRIVATE", "EMBEDDED"});
        result.put("capability_source", "hub_ai_capability_grant");
        result.put("metering_source", "hub_ai_metering");
        result.put("model_gateway_owner", "fenglin-ai-service");
        result.put("business_owner", "fenglin-intelligence-service");
        return result;
    }

    public Map<String, Object> reportOverview() {
        Map<String, Object> overview = biService.overview();
        Map<String, Object> result = new LinkedHashMap<>(overview);
        result.put("deploy_mode", deployMode);
        return result;
    }
}
