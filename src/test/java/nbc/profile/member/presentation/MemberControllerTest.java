package nbc.profile.member.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import nbc.profile.member.application.MemberService;
import nbc.profile.member.application.dto.ImageUploadCommand;
import nbc.profile.member.domain.exception.MemberNotFoundException;
import nbc.profile.member.domain.exception.ProfileImageNotFoundException;
import nbc.profile.member.presentation.dto.request.MemberCreateRequest;
import nbc.profile.member.presentation.dto.response.MemberResponse;
import nbc.profile.member.presentation.dto.response.ProfileImageUrlResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MemberController.class)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MemberService memberService;

    @Test
    @DisplayName("POST /api/members — 정상 입력 시 201 + SUCCESS envelope")
    void create_정상_201() throws Exception {
        MemberResponse resp = new MemberResponse(
                1L, "alice", 30, "INTJ", null,
                LocalDateTime.now(), LocalDateTime.now());
        given(memberService.create(any())).willReturn(resp);

        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MemberCreateRequest("alice", 30, "INTJ"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("alice"))
                .andExpect(jsonPath("$.data.mbti").value("INTJ"));
    }

    @Test
    @DisplayName("POST /api/members — name blank 시 400 + VALIDATION_FAILED + 필드 에러 목록")
    void create_name_blank_400() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MemberCreateRequest("", 30, "INTJ"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("POST /api/members — mbti 패턴 위반 시 400 + VALIDATION_FAILED")
    void create_mbti_invalid_400() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MemberCreateRequest("alice", 30, "XXXX"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/members — age 음수 시 400 + VALIDATION_FAILED")
    void create_age_음수_400() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MemberCreateRequest("alice", -1, "INTJ"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET /api/members/{id} — 존재 시 200 + SUCCESS")
    void get_정상_200() throws Exception {
        MemberResponse resp = new MemberResponse(
                1L, "alice", 30, "INTJ", null,
                LocalDateTime.now(), LocalDateTime.now());
        given(memberService.get(1L)).willReturn(resp);

        mockMvc.perform(get("/api/members/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("GET /api/members/{id} — Service 가 MemberNotFoundException 던지면 404 + MEMBER_NOT_FOUND")
    void get_미존재_404() throws Exception {
        given(memberService.get(9999L)).willThrow(new MemberNotFoundException());

        mockMvc.perform(get("/api/members/{id}", 9999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/members/{id}/profile-image — multipart 정상 시 200 + SUCCESS")
    void uploadProfileImage_정상_200() throws Exception {
        MemberResponse resp = new MemberResponse(
                1L, "alice", 30, "INTJ", "profile/1/abc.png",
                LocalDateTime.now(), LocalDateTime.now());
        given(memberService.updateProfileImage(anyLong(), any(ImageUploadCommand.class)))
                .willReturn(resp);

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", MediaType.IMAGE_PNG_VALUE, "hello".getBytes());

        mockMvc.perform(multipart("/api/members/{id}/profile-image", 1L).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.profileImageKey").value("profile/1/abc.png"));

        verify(memberService).updateProfileImage(anyLong(), any(ImageUploadCommand.class));
    }

    @Test
    @DisplayName("GET /api/members/{id}/profile-image — 정상 시 imageUrl + expiresAt")
    void getProfileImageUrl_정상_200() throws Exception {
        ProfileImageUrlResponse resp = new ProfileImageUrlResponse(
                "http://in-memory.test/profile/1/abc.png",
                LocalDateTime.now().plusMinutes(5));
        given(memberService.getProfileImageUrl(1L)).willReturn(resp);

        mockMvc.perform(get("/api/members/{id}/profile-image", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.imageUrl").value("http://in-memory.test/profile/1/abc.png"))
                .andExpect(jsonPath("$.data.expiresAt").exists());
    }

    @Test
    @DisplayName("GET /api/members/{id}/profile-image — 미등록 시 404 + PROFILE_IMAGE_NOT_FOUND")
    void getProfileImageUrl_미등록_404() throws Exception {
        given(memberService.getProfileImageUrl(1L))
                .willThrow(new ProfileImageNotFoundException());

        mockMvc.perform(get("/api/members/{id}/profile-image", 1L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_IMAGE_NOT_FOUND"));
    }
}
