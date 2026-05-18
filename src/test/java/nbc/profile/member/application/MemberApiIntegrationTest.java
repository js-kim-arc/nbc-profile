package nbc.profile.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import nbc.profile.member.presentation.dto.request.MemberCreateRequest;
import nbc.profile.shared.infrastructure.storage.InMemoryFileStorageAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class MemberApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryFileStorageAdapter inMemoryFileStorageAdapter;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("회원 생성 → ID 로 조회 시 동일 데이터")
    void 회원_생성_조회_시나리오() throws Exception {
        MvcResult created = mockMvc().perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MemberCreateRequest("alice", 30, "INTJ"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();

        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = body.path("data").path("id").asLong();
        assertThat(id).isPositive();

        mockMvc().perform(get("/api/members/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.name").value("alice"))
                .andExpect(jsonPath("$.data.mbti").value("INTJ"));
    }

    @Test
    @DisplayName("이미지 업로드 → InMemoryFileStorageAdapter 에 byte 보관 + URL 조회 시 in-memory.test 도메인 반환")
    void 이미지_업로드_그리고_URL_조회() throws Exception {
        // 회원 생성
        MvcResult created = mockMvc().perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MemberCreateRequest("bob", 25, "ENFP"))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 이미지 업로드
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", MediaType.IMAGE_PNG_VALUE, "image-bytes".getBytes());
        MvcResult uploaded = mockMvc().perform(multipart("/api/members/{id}/profile-image", id).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.profileImageKey").exists())
                .andReturn();
        String key = objectMapper.readTree(uploaded.getResponse().getContentAsString())
                .path("data").path("profileImageKey").asText();

        assertThat(key).startsWith("profile/" + id + "/").endsWith(".png");
        assertThat(inMemoryFileStorageAdapter.exists(key)).isTrue();
        assertThat(new String(inMemoryFileStorageAdapter.download(key))).isEqualTo("image-bytes");

        // URL 조회
        mockMvc().perform(get("/api/members/{id}/profile-image", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.imageUrl").value(
                        org.hamcrest.Matchers.startsWith("http://in-memory.test/")))
                .andExpect(jsonPath("$.data.expiresAt").exists());
    }

    @Test
    @DisplayName("GET /api/members/{id} 미존재 시 404 + MEMBER_NOT_FOUND")
    void 미존재_회원_조회_404() throws Exception {
        mockMvc().perform(get("/api/members/{id}", 9999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("프로필 미등록 회원 URL 조회 시 404 + PROFILE_IMAGE_NOT_FOUND")
    void 프로필_미등록_URL_조회_404() throws Exception {
        MvcResult created = mockMvc().perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MemberCreateRequest("carol", 28, "ISFP"))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc().perform(get("/api/members/{id}/profile-image", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_IMAGE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/members validation 실패 시 400 + VALIDATION_FAILED")
    void validation_실패_400() throws Exception {
        mockMvc().perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MemberCreateRequest("", 30, "INTJ"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
