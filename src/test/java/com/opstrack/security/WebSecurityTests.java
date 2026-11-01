package com.opstrack.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"h2", "test"})
class WebSecurityTests {

    private static final String OTHER_ORIGIN = "https://other-site.example";

    @Autowired
    private MockMvc mockMvc;

    @Value("${opstrack.security.api-key}")
    private String apiKey;

    @Test
    void successfulResponsesHaveSecurityHeaders() throws Exception {
        expectSecurityHeaders(mockMvc.perform(get("/api/incidents").header(ApiKeyFilter.HEADER, apiKey)));
    }

    @Test
    void rejectedResponsesHaveSecurityHeaders() throws Exception {
        expectSecurityHeaders(mockMvc.perform(get("/api/incidents")).andExpect(status().isUnauthorized()));
    }

    @Test
    void healthHasSecurityHeaders() throws Exception {
        expectSecurityHeaders(mockMvc.perform(get("/actuator/health")));
    }

    @Test
    void otherOriginsGetNoCorsPermission() throws Exception {
        mockMvc.perform(get("/api/incidents")
                        .header(ApiKeyFilter.HEADER, apiKey)
                        .header("Origin", OTHER_ORIGIN))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mockMvc.perform(options("/api/incidents")
                        .header("Origin", OTHER_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void actuatorDoesNotListItsEndpoints() throws Exception {
        mockMvc.perform(get("/actuator")).andExpect(status().isNotFound());
    }

    private void expectSecurityHeaders(ResultActions result) throws Exception {
        result.andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
