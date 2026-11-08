package com.fenglin.auth.web;

import com.fenglin.auth.config.AuthProperties;
import com.fenglin.auth.service.JwtKeyProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HUB-API-101 / 102：OIDC 发现文档与 JWKS。
 */
@RestController
public class WellKnownController {

    private final AuthProperties properties;
    private final JwtKeyProvider keyProvider;

    public WellKnownController(AuthProperties properties, JwtKeyProvider keyProvider) {
        this.properties = properties;
        this.keyProvider = keyProvider;
    }

    @GetMapping("/.well-known/openid-configuration")
    public Map<String, Object> discovery() {
        String issuer = properties.getIssuer();
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("issuer", issuer);
        doc.put("authorization_endpoint", issuer + "/api/hub/v1/sso/authorize");
        doc.put("token_endpoint", issuer + "/api/hub/v1/sso/token");
        doc.put("userinfo_endpoint", issuer + "/api/hub/v1/sso/userinfo");
        doc.put("revocation_endpoint", issuer + "/api/hub/v1/sso/token:revoke");
        doc.put("end_session_endpoint", issuer + "/api/hub/v1/sso/logout");
        doc.put("jwks_uri", issuer + "/.well-known/jwks.json");
        doc.put("response_types_supported", List.of("code"));
        doc.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        doc.put("subject_types_supported", List.of("public"));
        doc.put("id_token_signing_alg_values_supported", List.of("RS256"));
        doc.put("scopes_supported", List.of("openid", "profile"));
        doc.put("token_endpoint_auth_methods_supported", List.of("client_secret_basic", "none"));
        return doc;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return keyProvider.jwks();
    }
}
