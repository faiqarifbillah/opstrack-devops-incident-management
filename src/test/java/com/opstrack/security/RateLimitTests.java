package com.opstrack.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Each test uses its own client IP, so one test's requests cannot affect another. */
@SpringBootTest(properties = "opstrack.security.rate-limit-per-minute=3")
@AutoConfigureMockMvc
@ActiveProfiles({"h2", "test"})
class RateLimitTests {

    @Autowired
    private MockMvc mockMvc;

    @Value("${opstrack.security.api-key}")
    private String apiKey;

    @Test
    void requestOverTheLimitReturns429WithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(authorized("10.0.0.1")).andExpect(status().isOk());
        }

        mockMvc.perform(authorized("10.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void clientsAreLimitedSeparately() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(authorized("10.0.0.2"));
        }

        mockMvc.perform(authorized("10.0.0.3")).andExpect(status().isOk());
    }

    @Test
    void failedKeyGuessesCountTowardsTheLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(fromClient("10.0.0.4").header(ApiKeyFilter.HEADER, "guess-" + i))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(fromClient("10.0.0.4").header(ApiKeyFilter.HEADER, "guess-3"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void healthIsNotRateLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/actuator/health").with(request -> {
                request.setRemoteAddr("10.0.0.5");
                return request;
            })).andExpect(status().isOk());
        }
    }

    private MockHttpServletRequestBuilder authorized(String client) {
        return fromClient(client).header(ApiKeyFilter.HEADER, apiKey);
    }

    private MockHttpServletRequestBuilder fromClient(String client) {
        return get("/api/incidents").with(request -> {
            request.setRemoteAddr(client);
            return request;
        });
    }
}
