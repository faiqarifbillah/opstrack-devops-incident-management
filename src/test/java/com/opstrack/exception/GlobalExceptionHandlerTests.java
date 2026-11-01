package com.opstrack.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.opstrack.security.ApiKeyFilter;
import com.opstrack.service.IncidentService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"h2", "test"})
class GlobalExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentService service;

    @Value("${opstrack.security.api-key}")
    private String apiKey;

    @Test
    void unexpectedErrorReturns500WithoutInternalDetails() throws Exception {
        when(service.findAll()).thenThrow(new RuntimeException("connection to db-host failed for user admin"));

        mockMvc.perform(get("/api/incidents").header(ApiKeyFilter.HEADER, apiKey))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString("db-host"))));
    }
}
