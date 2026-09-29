package com.dylanjohnpratt.paradise.be.integration;

import com.dylanjohnpratt.paradise.be.dto.LoginRequest;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.UserRepository;
import com.dylanjohnpratt.paradise.be.service.DocsFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for {@code /docs/**} against a temp-dir vault fixture ({@link DocsFixture}):
 * auth, per-role tree filtering, text/raw/embed responses and headers, and refresh.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class DocsControllerIntegrationTest {

    private static Path fixtureDir;
    private static DocsFixture fixture;

    @DynamicPropertySource
    static void docsProperties(DynamicPropertyRegistry registry) {
        try {
            fixtureDir = Files.createTempDirectory("docs-it-");
            fixture = DocsFixture.create(fixtureDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        registry.add("docs.path", () -> fixture.docs.toString());
        registry.add("docs.le-path", () -> fixture.le.toString());
        registry.add("docs.pratt-capitol-path", () -> "");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:docsit;DB_CLOSE_DELAY=-1");
    }

    @AfterAll
    static void deleteFixture() throws IOException {
        DocsFixture.deleteTree(fixtureDir);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        userRepository.save(new User("docsadmin", passwordEncoder.encode("adminpass"), Set.of("ROLE_ADMIN", "ROLE_USER")));
        userRepository.save(new User("docsuser", passwordEncoder.encode("userpass"), Set.of("ROLE_USER")));
        adminToken = obtainToken("docsadmin", "adminpass");
        userToken = obtainToken("docsuser", "userpass");
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

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    @DisplayName("No token: every docs endpoint is 403")
    void noTokenIsForbidden() throws Exception {
        mockMvc.perform(get("/docs/tree")).andExpect(status().isForbidden());
        mockMvc.perform(get("/docs/raw").param("path", "Home/Screenshot 1.png")).andExpect(status().isForbidden());
        mockMvc.perform(get("/docs/embed").param("from", "Home/Basement.md").param("target", "shared.png"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/docs/refresh")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin tree includes LE Docs with root=true; other nodes omit root; files keep children=null")
    void adminTree() throws Exception {
        mockMvc.perform(get("/docs/tree").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(""))
                .andExpect(jsonPath("$.path").value(""))
                .andExpect(jsonPath("$.root").doesNotExist())
                .andExpect(jsonPath("$.children[?(@.name=='LE Docs')].root").value(true))
                .andExpect(jsonPath("$.children[?(@.name=='LE Docs')].path").value("LE Docs"))
                .andExpect(jsonPath("$.children[?(@.name=='Home')].root").doesNotExist())
                .andExpect(jsonPath("$.children[?(@.name=='Alpha.md')].type").value("file"))
                .andExpect(content().string(containsString(
                        "{\"name\":\"Alpha.md\",\"type\":\"file\",\"path\":\"Alpha.md\",\"children\":null}")))
                .andExpect(jsonPath("$.children[?(@.name=='.trash')]").isEmpty())
                .andExpect(jsonPath("$.children[?(@.name=='README.txt')]").isEmpty());
    }

    @Test
    @DisplayName("User tree has no LE Docs, and LE paths are 404 for a user")
    void userTreeAndLeAccess() throws Exception {
        mockMvc.perform(get("/docs/tree").header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children[?(@.name=='LE Docs')]").isEmpty())
                .andExpect(jsonPath("$.children[?(@.name=='Home')]").isNotEmpty());

        mockMvc.perform(get("/docs/raw")
                        .param("path", "LE Docs/IP Management/Lamaryah WorldBuilding/Attachments/icon.png")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DOCS_FILE_NOT_FOUND"));

        mockMvc.perform(get("/docs/raw")
                        .param("path", "LE Docs/IP Management/Lamaryah WorldBuilding/Attachments/icon.png")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Raw PNG: image/png, inline, no-store, nosniff, exact bytes, Range supported")
    void rawPng() throws Exception {
        mockMvc.perform(get("/docs/raw").param("path", "Home/Screenshot 1.png")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Content-Disposition", startsWith("inline")))
                .andExpect(header().string("Content-Disposition", containsString("Screenshot%201.png")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Length", String.valueOf(DocsFixture.PNG_BYTES.length)))
                .andExpect(content().bytes(DocsFixture.PNG_BYTES));

        mockMvc.perform(get("/docs/raw").param("path", "Home/Screenshot 1.png")
                        .header("Range", "bytes=0-3")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isPartialContent())
                .andExpect(content().bytes(Arrays.copyOfRange(DocsFixture.PNG_BYTES, 0, 4)));
    }

    @Test
    @DisplayName("Raw PDF is application/pdf inline; wrong types are 400; traversal is 403")
    void rawPdfTypesAndTraversal() throws Exception {
        mockMvc.perform(get("/docs/raw").param("path", "Insurance/Car Insurance/ID Card.pdf")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", startsWith("inline")));

        mockMvc.perform(get("/docs/raw").param("path", "Home/Basement.md")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DOCS_INVALID_FILE_TYPE"));

        mockMvc.perform(get("/docs/file").param("path", "../Welcome.md")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("DOCS_PATH_TRAVERSAL"));

        mockMvc.perform(get("/docs/file").param("path", ".trash/x.md")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Text: md is text/markdown UTF-8, canvas is application/json")
    void textFiles() throws Exception {
        mockMvc.perform(get("/docs/file").param("path", "Home/Basement.md")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/markdown")))
                .andExpect(header().string("Content-Type", containsString("charset=UTF-8")))
                .andExpect(content().string(startsWith("# Basement")));

        mockMvc.perform(get("/docs/file").param("path", "Home/Network/Network Diagram.canvas")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.nodes[0].file").value("Attachments/server.png"));
    }

    @Test
    @DisplayName("Embed: canvas in Documentation/ resolves the vault-level Attachments/ file")
    void embedResolves() throws Exception {
        mockMvc.perform(get("/docs/embed")
                        .param("from", "Home/Network/Network Diagram.canvas")
                        .param("target", "Attachments/server.png")
                        .param("literal", "true")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Content-Disposition", startsWith("inline")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().bytes(DocsFixture.PNG_BYTES));

        mockMvc.perform(get("/docs/embed")
                        .param("from", DocsFixture.LE_AMIRA_NOTE)
                        .param("target", "IP Management/Lamaryah WorldBuilding/Attachments/Pictures/Amira/Official_Amira_Artwork2.jpg")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));

        mockMvc.perform(get("/docs/embed")
                        .param("from", "Home/Basement.md")
                        .param("target", "unreferenced.png")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SVG is served with a sandboxing Content-Security-Policy")
    void svgSandboxed() throws Exception {
        mockMvc.perform(get("/docs/embed")
                        .param("from", "Home/Network/Network Diagram.canvas")
                        .param("target", "Attachments/logo.svg")
                        .param("literal", "true")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"))
                .andExpect(header().string("Content-Security-Policy", containsString("sandbox")))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")));
    }

    @Test
    @DisplayName("POST /docs/refresh returns 200 with the caller's filtered tree")
    void refresh() throws Exception {
        mockMvc.perform(post("/docs/refresh").header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children[?(@.name=='Home')]").isNotEmpty())
                .andExpect(jsonPath("$.children[?(@.name=='LE Docs')]").isEmpty());

        mockMvc.perform(post("/docs/refresh").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children[?(@.name=='LE Docs')].root").value(true));
    }
}
