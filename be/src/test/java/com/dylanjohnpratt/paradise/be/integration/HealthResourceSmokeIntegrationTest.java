package com.dylanjohnpratt.paradise.be.integration;

import com.dylanjohnpratt.paradise.be.dto.LoginRequest;
import com.dylanjohnpratt.paradise.be.repository.UserRepository;
import com.dylanjohnpratt.paradise.be.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end smoke tests for each health resource under
 * {@code /users/{userId}/health/**}. Exercises the full Spring MVC pipeline
 * (filters, auth, controllers, services, JPA, converters) against H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class HealthResourceSmokeIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private UserService userService;

    private String ownerToken;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        // Create via UserService so HealthMetricSeeder auto-provisions the 6 canonical metrics.
        userService.createUser("owner", "ownerpass", Set.of("ROLE_USER"));
        ownerToken = obtainToken("owner", "ownerpass");
    }

    private String obtainToken(String username, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest(username, password);
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(loginRequest))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    @DisplayName("Journal: upsert round-trips; second POST on same date overwrites")
    void journalUpsertRoundTrip() throws Exception {
        String body1 = "{\"date\":\"2025-03-14\",\"thoughts\":\"first\"}";
        String body2 = "{\"date\":\"2025-03-14\",\"thoughts\":\"second\"}";

        mockMvc.perform(post("/users/owner/health/journal")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thoughts").value("first"));

        mockMvc.perform(post("/users/owner/health/journal")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thoughts").value("second"));

        mockMvc.perform(get("/users/owner/health/journal")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].thoughts").value("second"));

        // CSV export uses StreamingResponseBody — verify the route handshakes correctly
        // (headers + async dispatch). Body content is asserted by the service-level CSV
        // unit tests because the streaming thread doesn't share the test transaction.
        MvcResult async = mockMvc.perform(get("/users/owner/health/journal/export")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(async))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", Objects.requireNonNull(org.hamcrest.Matchers.containsString("text/csv"))))
                .andExpect(header().string("Content-Disposition",
                        Objects.requireNonNull(org.hamcrest.Matchers.containsString("health-journal.csv"))));
    }

    @Test
    @DisplayName("Metrics: 6 seeded metrics auto-provisioned, seeded metric cannot be deleted")
    void metricsSeededOnUserCreate() throws Exception {
        mockMvc.perform(get("/users/owner/health/metrics")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[?(@.slug=='bp')].seeded").value(true));

        // Grab the bp metric id and try to delete it → 403 HEALTH_SEEDED_METRIC_LOCKED
        MvcResult listResult = mockMvc.perform(get("/users/owner/health/metrics")
                        .header("Authorization", "Bearer " + ownerToken))
                .andReturn();
        com.fasterxml.jackson.databind.JsonNode arr = objectMapper.readTree(listResult.getResponse().getContentAsString());
        String bpId = null;
        for (com.fasterxml.jackson.databind.JsonNode node : arr) {
            if ("bp".equals(node.get("slug").asText())) {
                bpId = node.get("id").asText();
                break;
            }
        }
        org.assertj.core.api.Assertions.assertThat(bpId).isNotNull();

        mockMvc.perform(delete("/users/owner/health/metrics/" + bpId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("HEALTH_SEEDED_METRIC_LOCKED"));
    }

    @Test
    @DisplayName("Metrics: append point to multi-series BP via /points (Systolic + Diastolic)")
    void multiSeriesAppendPointRoundTrip() throws Exception {
        // Find the seeded BP metric.
        MvcResult listResult = mockMvc.perform(get("/users/owner/health/metrics")
                        .header("Authorization", "Bearer " + ownerToken))
                .andReturn();
        com.fasterxml.jackson.databind.JsonNode arr = objectMapper.readTree(listResult.getResponse().getContentAsString());
        String bpId = null;
        for (com.fasterxml.jackson.databind.JsonNode node : arr) {
            if ("bp".equals(node.get("slug").asText())) {
                bpId = node.get("id").asText();
                break;
            }
        }
        org.assertj.core.api.Assertions.assertThat(bpId).isNotNull();

        String body = "{\"values\":["
                + "{\"label\":\"Systolic\",\"value\":120},"
                + "{\"label\":\"Diastolic\",\"value\":80}"
                + "],\"label\":\"2025-05-14\"}";

        mockMvc.perform(post("/users/owner/health/metrics/" + bpId + "/points")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasets[?(@.label=='Systolic')].data[0]").value(120))
                .andExpect(jsonPath("$.datasets[?(@.label=='Diastolic')].data[0]").value(80))
                .andExpect(jsonPath("$.labels[0]").value("2025-05-14"));
    }

    @Test
    @DisplayName("Metrics: multi-series append rejects mismatched dataset labels")
    void multiSeriesAppendPointMismatchRejected() throws Exception {
        MvcResult listResult = mockMvc.perform(get("/users/owner/health/metrics")
                        .header("Authorization", "Bearer " + ownerToken))
                .andReturn();
        com.fasterxml.jackson.databind.JsonNode arr = objectMapper.readTree(listResult.getResponse().getContentAsString());
        String bpId = null;
        for (com.fasterxml.jackson.databind.JsonNode node : arr) {
            if ("bp".equals(node.get("slug").asText())) {
                bpId = node.get("id").asText();
                break;
            }
        }

        // Missing "Diastolic" — must be rejected.
        String body = "{\"values\":[{\"label\":\"Systolic\",\"value\":120}]}";

        mockMvc.perform(post("/users/owner/health/metrics/" + bpId + "/points")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("HEALTH_VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Metrics: line-type with datasets payload rejected 400 HEALTH_VALIDATION_FAILED")
    void metricLineWithDatasetsRejected() throws Exception {
        String body = "{\"name\":\"Custom\",\"type\":\"line\",\"datasets\":[{\"label\":\"a\",\"data\":[1]}]}";
        mockMvc.perform(post("/users/owner/health/metrics")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("HEALTH_VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Documents: upload, download (exact bytes), list, delete")
    void documentUploadDownloadDeleteRoundTrip() throws Exception {
        byte[] pdfBytes = "%PDF-1.4 fake content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", pdfBytes);

        MvcResult created = mockMvc.perform(multipart("/users/owner/health/documents")
                        .file(file)
                        .param("category", "Lab Results")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("report.pdf"))
                .andReturn();

        String docId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        // List should now contain exactly one document.
        mockMvc.perform(get("/users/owner/health/documents")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Download returns identical bytes (StreamingResponseBody → async dispatch).
        MvcResult asyncDownload = mockMvc.perform(get("/users/owner/health/documents/" + docId + "/download")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(asyncDownload))
                .andExpect(status().isOk())
                .andExpect(content().bytes(Objects.requireNonNull(pdfBytes)));

        // Delete.
        mockMvc.perform(delete("/users/owner/health/documents/" + docId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/users/owner/health/documents")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Documents: unsupported MIME returns 415 HEALTH_DOC_UNSUPPORTED")
    void documentUnsupportedMimeRejected() throws Exception {
        MockMultipartFile exe = new MockMultipartFile(
                "file", "virus.exe", "application/octet-stream", "MZ".getBytes());

        mockMvc.perform(multipart("/users/owner/health/documents")
                        .file(exe)
                        .param("category", "Other")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("HEALTH_DOC_UNSUPPORTED"));
    }

    @Test
    @DisplayName("Appointments: create then list")
    void appointmentsCreateList() throws Exception {
        String body = "{\"doctor\":\"Dr. Rivera\",\"specialty\":\"Cardiology\","
                + "\"apptDate\":\"2025-06-15T10:30:00\",\"type\":\"upcoming\",\"notes\":\"fasting\"}";

        mockMvc.perform(post("/users/owner/health/appointments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doctor").value("Dr. Rivera"));

        mockMvc.perform(get("/users/owner/health/appointments")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Reminders: create then list")
    void remindersCreateList() throws Exception {
        String body = "{\"title\":\"Refill Rx\",\"description\":\"pharmacy\","
                + "\"dueAt\":\"2025-05-01T09:00:00\"}";

        mockMvc.perform(post("/users/owner/health/reminders")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Refill Rx"))
                .andExpect(jsonPath("$.completed").value(false));

        mockMvc.perform(get("/users/owner/health/reminders")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
