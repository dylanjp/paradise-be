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

import static com.dylanjohnpratt.paradise.be.service.DocsFixture.h;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for {@code /docs/**} against a temp-dir copy of the consolidated vault ({@link DocsFixture}):
 * auth, per-role section filtering, text/raw/embed responses and headers, and refresh.
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
        registry.add("docs.vault-path", () -> fixture.vault.toString());
        registry.add("docs.folders", () -> DocsFixture.HOME);
        registry.add("docs.admin-folders", () -> DocsFixture.PERSONAL + "," + DocsFixture.LLCS);
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
        mockMvc.perform(get("/docs/raw").param("path", h("Home/Screenshot 1.png"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/docs/embed").param("from", h("Home/Basement.md")).param("target", "shared.png"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/docs/refresh")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin tree has all three sections with root=true; other nodes omit root; files keep children=null")
    void adminTree() throws Exception {
        mockMvc.perform(get("/docs/tree").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(""))
                .andExpect(jsonPath("$.path").value(""))
                .andExpect(jsonPath("$.root").doesNotExist())
                .andExpect(jsonPath("$.children.length()").value(3))
                .andExpect(jsonPath("$.children[0].name").value("Dylan Personal"))
                .andExpect(jsonPath("$.children[1].name").value("Home and Family"))
                .andExpect(jsonPath("$.children[2].name").value("LLCs"))
                .andExpect(jsonPath("$.children[?(@.name=='LLCs')].root").value(true))
                .andExpect(jsonPath("$.children[?(@.name=='LLCs')].path").value("LLCs"))
                .andExpect(jsonPath("$.children[?(@.name=='Home and Family')].root").value(true))
                .andExpect(jsonPath("$.children[1].children[?(@.name=='Home')].root").doesNotExist())
                .andExpect(content().string(containsString(
                        "{\"name\":\"Alpha.md\",\"type\":\"file\",\"path\":\"Home and Family/Alpha.md\",\"children\":null}")))
                .andExpect(content().string(not(containsString(".trash"))))
                .andExpect(content().string(not(containsString("README.txt"))))
                .andExpect(content().string(not(containsString("Attachments/server.png"))));
    }

    @Test
    @DisplayName("User tree has only Home and Family; admin-only sections are 404 for a user")
    void userTreeAndAdminSectionAccess() throws Exception {
        mockMvc.perform(get("/docs/tree").header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children.length()").value(1))
                .andExpect(jsonPath("$.children[0].name").value("Home and Family"))
                .andExpect(content().string(not(containsString("LLCs"))))
                .andExpect(content().string(not(containsString("Dylan Personal"))));

        for (String path : new String[] {
                DocsFixture.LE + "/IP Management/Lamaryah WorldBuilding/Attachments/icon.png",
                "Dylan Personal/Accounts/private.png"}) {
            mockMvc.perform(get("/docs/raw").param("path", path).header("Authorization", bearer(userToken)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("DOCS_FILE_NOT_FOUND"));
            mockMvc.perform(get("/docs/raw").param("path", path).header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/docs/file").param("path", "Dylan Personal/Accounts/Accounts.md")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isNotFound());

        // An open note that embeds an image stored in an admin-only section does not expose it
        mockMvc.perform(get("/docs/embed")
                        .param("from", h("Home/Basement.md"))
                        .param("target", "private.png")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/docs/embed")
                        .param("from", h("Home/Basement.md"))
                        .param("target", "private.png")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        // Stepping out of the open section is a traversal, never a way in
        mockMvc.perform(get("/docs/file").param("path", h("../Dylan Personal/Accounts/Accounts.md"))
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("DOCS_PATH_TRAVERSAL"));
    }

    @Test
    @DisplayName("Raw PNG: image/png, inline, no-store, nosniff, exact bytes, Range supported")
    void rawPng() throws Exception {
        mockMvc.perform(get("/docs/raw").param("path", h("Home/Screenshot 1.png"))
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Content-Disposition", startsWith("inline")))
                .andExpect(header().string("Content-Disposition", containsString("Screenshot%201.png")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Length", String.valueOf(DocsFixture.PNG_BYTES.length)))
                .andExpect(content().bytes(DocsFixture.PNG_BYTES));

        mockMvc.perform(get("/docs/raw").param("path", h("Home/Screenshot 1.png"))
                        .header("Range", "bytes=0-3")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isPartialContent())
                .andExpect(content().bytes(Arrays.copyOfRange(DocsFixture.PNG_BYTES, 0, 4)));
    }

    @Test
    @DisplayName("Raw PDF is application/pdf inline; wrong types are 400; traversal is 403")
    void rawPdfTypesAndTraversal() throws Exception {
        mockMvc.perform(get("/docs/raw").param("path", h("Insurance/Car Insurance/ID Card.pdf"))
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", startsWith("inline")));

        mockMvc.perform(get("/docs/raw").param("path", h("Home/Basement.md"))
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DOCS_INVALID_FILE_TYPE"));

        mockMvc.perform(get("/docs/file").param("path", h("../Welcome.md"))
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("DOCS_PATH_TRAVERSAL"));

        mockMvc.perform(get("/docs/file").param("path", h(".trash/x.md"))
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Text: md is text/markdown UTF-8, canvas is application/json")
    void textFiles() throws Exception {
        mockMvc.perform(get("/docs/file").param("path", h("Home/Basement.md"))
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/markdown")))
                .andExpect(header().string("Content-Type", containsString("charset=UTF-8")))
                .andExpect(content().string(startsWith("# Basement")));

        mockMvc.perform(get("/docs/file").param("path", h("Home/Network/Network Diagram.canvas"))
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.nodes[0].file").value("Attachments/server.png"));
    }

    @Test
    @DisplayName("Embed: a section's canvas resolves the vault-level Attachments/ file")
    void embedResolves() throws Exception {
        mockMvc.perform(get("/docs/embed")
                        .param("from", h("Home/Network/Network Diagram.canvas"))
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
                        .param("from", h("Home/Basement.md"))
                        .param("target", "unreferenced.png")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SVG is served with a sandboxing Content-Security-Policy")
    void svgSandboxed() throws Exception {
        mockMvc.perform(get("/docs/embed")
                        .param("from", h("Home/Network/Network Diagram.canvas"))
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
                .andExpect(jsonPath("$.children[?(@.name=='Home and Family')]").isNotEmpty())
                .andExpect(jsonPath("$.children[?(@.name=='LLCs')]").isEmpty());

        mockMvc.perform(post("/docs/refresh").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children[?(@.name=='LLCs')].root").value(true))
                .andExpect(jsonPath("$.children[?(@.name=='Dylan Personal')].root").value(true));
    }
}
