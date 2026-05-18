package nbc.profile.member.presentation;

import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import nbc.profile.common.web.ApiResponse;
import nbc.profile.member.application.MemberService;
import nbc.profile.member.application.dto.ImageUploadCommand;
import nbc.profile.member.presentation.dto.request.MemberCreateRequest;
import nbc.profile.member.presentation.dto.response.MemberResponse;
import nbc.profile.member.presentation.dto.response.ProfileImageUrlResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping
    public ResponseEntity<ApiResponse<MemberResponse>> create(
            @Valid @RequestBody MemberCreateRequest request) {
        MemberResponse resp = memberService.create(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(resp));
    }

    @GetMapping("/{id}")
    public ApiResponse<MemberResponse> get(@PathVariable Long id) {
        return ApiResponse.success(memberService.get(id));
    }

    @PostMapping(value = "/{id}/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MemberResponse> uploadProfileImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        ImageUploadCommand cmd = new ImageUploadCommand(
                file.getBytes(), file.getContentType(), file.getOriginalFilename());
        return ApiResponse.success(memberService.updateProfileImage(id, cmd));
    }

    @GetMapping("/{id}/profile-image")
    public ApiResponse<ProfileImageUrlResponse> getProfileImageUrl(@PathVariable Long id) {
        return ApiResponse.success(memberService.getProfileImageUrl(id));
    }
}
