package com.opstrack.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.ObjectMapper;
import com.opstrack.repository.IncidentRepository;
import com.opstrack.security.ApiKeyFilter;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"h2", "test"})
class IncidentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IncidentRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${opstrack.security.api-key}")
    private String apiKey;

    @BeforeEach
    void clearDatabase() {
        repository.deleteAll();
    }

    @Test
    void createReturns201AndDefaultsToOpen() throws Exception {
        send(post("/api/incidents"), body("API failure", "HIGH", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("API failure"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void findAllReturnsEveryIncident() throws Exception {
        create("First", "LOW", "OPEN");
        create("Second", "HIGH", "RESOLVED");

        send(get("/api/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("First"))
                .andExpect(jsonPath("$[1].title").value("Second"));
    }

    @Test
    void findByIdReturnsTheIncident() throws Exception {
        long id = create("Database down", "CRITICAL", "OPEN");

        send(get("/api/incidents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Database down"));
    }

    @Test
    void updateChangesFieldsAndUpdatedAt() throws Exception {
        long id = create("Slow API", "MEDIUM", "OPEN");
        String createdResponse = send(get("/api/incidents/{id}", id))
                .andReturn().getResponse().getContentAsString();
        String originalUpdatedAt = objectMapper.readTree(createdResponse).get("updatedAt").asString();
        Thread.sleep(5);

        send(put("/api/incidents/{id}", id), body("Slow API", "HIGH", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(result -> {
                    String updatedAt = objectMapper
                            .readTree(result.getResponse().getContentAsString())
                            .get("updatedAt").asString();
                    assertThat(updatedAt).isNotEqualTo(originalUpdatedAt);
                });
    }

    @Test
    void deleteRemovesTheIncident() throws Exception {
        long id = create("Temporary", "LOW", "OPEN");

        send(delete("/api/incidents/{id}", id))
                .andExpect(status().isNoContent());
        send(get("/api/incidents/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void filtersByStatus() throws Exception {
        create("Open one", "LOW", "OPEN");
        create("Resolved one", "LOW", "RESOLVED");

        send(get("/api/incidents/status/RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Resolved one"));
    }

    @Test
    void filtersBySeverity() throws Exception {
        create("Minor", "LOW", "OPEN");
        create("Major", "CRITICAL", "OPEN");

        send(get("/api/incidents/severity/CRITICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Major"));
    }

    @Test
    void unknownIdReturns404ForGetPutAndDelete() throws Exception {
        send(get("/api/incidents/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Incident not found: 999999"))
                .andExpect(jsonPath("$.path").value("/api/incidents/999999"));
        send(put("/api/incidents/999999"), body("Missing", "LOW", "OPEN"))
                .andExpect(status().isNotFound());
        send(delete("/api/incidents/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingRequiredFieldsReturn400WithFieldErrors() throws Exception {
        send(post("/api/incidents"), "{\"title\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.title").value("title is required"))
                .andExpect(jsonPath("$.fieldErrors.description").value("description is required"))
                .andExpect(jsonPath("$.fieldErrors.severity").value("severity is required"));
    }

    @Test
    void tooLongTitleReturns400() throws Exception {
        send(post("/api/incidents"), body("x".repeat(201), "LOW", "OPEN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").value("title must be at most 200 characters"));
    }

    @Test
    void updateIsValidatedToo() throws Exception {
        long id = create("Valid", "LOW", "OPEN");

        send(put("/api/incidents/{id}", id), "{\"title\":\"Only a title\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownEnumValueInBodyReturns400() throws Exception {
        send(post("/api/incidents"), body("Bad severity", "SEVERE", "OPEN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("unknown enum value")));
    }

    @Test
    void malformedJsonReturns400WithoutInternalDetails() throws Exception {
        send(post("/api/incidents"), "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void invalidPathValuesReturn400() throws Exception {
        send(get("/api/incidents/status/BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for 'status'"));
        send(get("/api/incidents/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for 'id'"));
    }

    @Test
    void unsupportedMethodReturns405NotA500() throws Exception {
        send(delete("/api/incidents"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void clientCannotSetServerControlledFields() throws Exception {
        String json = "{\"id\":424242,\"createdAt\":\"2000-01-01T00:00:00Z\",\"title\":\"Spoof\","
                + "\"description\":\"Test incident\",\"severity\":\"LOW\"}";

        send(post("/api/incidents"), json)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(424242)))
                .andExpect(jsonPath("$.createdAt", not("2000-01-01T00:00:00Z")));
    }

    @Test
    void updateKeepsCreatedAtAndSavesResolutionFields() throws Exception {
        long id = create("Outage", "HIGH", "OPEN");
        String createdResponse = send(get("/api/incidents/{id}", id))
                .andReturn().getResponse().getContentAsString();
        String createdAt = objectMapper.readTree(createdResponse).get("createdAt").asString();
        String json = "{\"title\":\"Outage\",\"description\":\"Test incident\",\"severity\":\"HIGH\","
                + "\"status\":\"RESOLVED\",\"assignedTo\":\"On-call\",\"resolutionNotes\":\"Restarted\"}";

        send(put("/api/incidents/{id}", id), json).andExpect(status().isOk());

        send(get("/api/incidents/{id}", id))
                .andExpect(jsonPath("$.createdAt").value(createdAt))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.assignedTo").value("On-call"))
                .andExpect(jsonPath("$.resolutionNotes").value("Restarted"));
    }

    @Test
    void titleAtTheMaximumLengthIsAccepted() throws Exception {
        send(post("/api/incidents"), body("x".repeat(200), "LOW", "OPEN"))
                .andExpect(status().isCreated());
    }

    @Test
    void wrongContentTypeReturns415() throws Exception {
        send(post("/api/incidents").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    private ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(ApiKeyFilter.HEADER, apiKey));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return send(request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long create(String title, String severity, String status) throws Exception {
        String response = send(post("/api/incidents"), body(title, severity, status))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private String body(String title, String severity, String status) {
        String statusField = status == null ? "" : ",\"status\":\"" + status + "\"";
        return "{\"title\":\"" + title + "\",\"description\":\"Test incident\",\"severity\":\""
                + severity + "\"" + statusField + "}";
    }
}
