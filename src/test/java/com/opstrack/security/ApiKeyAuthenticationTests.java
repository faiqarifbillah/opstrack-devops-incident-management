package com.opstrack.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"h2", "test"})
@ExtendWith(OutputCaptureExtension.class)
class ApiKeyAuthenticationTests {

    @Autowired
    private MockMvc mockMvc;

    @Value("${opstrack.security.api-key}")
    private String apiKey;

    @Test
    void missingKeyReturns401AsJson() throws Exception {
        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Missing or invalid API key"))
                .andExpect(jsonPath("$.path").value("/api/incidents"));
    }

    @Test
    void invalidKeyReturns401AndIsNotEchoedBack() throws Exception {
        mockMvc.perform(get("/api/incidents").header(ApiKeyFilter.HEADER, "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString("wrong-key"))));
    }

    @Test
    void keyWithExtraCharactersReturns401() throws Exception {
        mockMvc.perform(get("/api/incidents").header(ApiKeyFilter.HEADER, apiKey + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validKeyIsAccepted() throws Exception {
        mockMvc.perform(get("/api/incidents").header(ApiKeyFilter.HEADER, apiKey))
                .andExpect(status().isOk());
    }

    @Test
    void healthDoesNotNeedAKey() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void keysAreNeverWrittenToTheLogs(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/incidents").header(ApiKeyFilter.HEADER, "guessed-key-value"));
        mockMvc.perform(get("/api/incidents").header(ApiKeyFilter.HEADER, apiKey));

        assertThat(output).contains("invalid API key");
        assertThat(output).doesNotContain("guessed-key-value");
        assertThat(output).doesNotContain(apiKey);
    }

    @Test
    void missingOrShortConfiguredKeyStopsStartup() {
        ObjectMapper objectMapper = new ObjectMapper();

        assertThatThrownBy(() -> new ApiKeyFilter("", objectMapper))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ApiKeyFilter(null, objectMapper))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ApiKeyFilter("change-me", objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("change-me");
    }
}
