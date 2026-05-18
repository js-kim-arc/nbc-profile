package nbc.profile.member.application;

import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nbc.profile.member.application.dto.ImageUploadCommand;
import nbc.profile.member.application.dto.MemberCreateCommand;
import nbc.profile.member.domain.Mbti;
import nbc.profile.member.domain.Member;
import nbc.profile.member.domain.exception.MemberNotFoundException;
import nbc.profile.member.domain.exception.ProfileImageNotFoundException;
import nbc.profile.member.presentation.dto.response.MemberResponse;
import nbc.profile.member.presentation.dto.response.ProfileImageUrlResponse;
import nbc.profile.member.repository.MemberRepository;
import nbc.profile.shared.application.port.out.FileStoragePort;
import nbc.profile.shared.infrastructure.storage.S3StorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final FileStoragePort fileStoragePort;
    private final S3StorageProperties s3Properties;

    public MemberResponse create(MemberCreateCommand cmd) {
        Member saved = memberRepository.save(
                Member.create(cmd.name(), cmd.age(), Mbti.valueOf(cmd.mbti())));
        return MemberResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public MemberResponse get(Long id) {
        return MemberResponse.from(findOrThrow(id));
    }

    public MemberResponse updateProfileImage(Long memberId, ImageUploadCommand cmd) {
        Member member = findOrThrow(memberId);
        String key = generateKey(memberId, cmd.originalFilename());
        fileStoragePort.upload(key, cmd.bytes(), cmd.contentType());
        member.updateProfileImageKey(key);
        return MemberResponse.from(member);
    }

    @Transactional(readOnly = true)
    public ProfileImageUrlResponse getProfileImageUrl(Long memberId) {
        Member member = findOrThrow(memberId);
        if (member.getProfileImageKey() == null) {
            throw new ProfileImageNotFoundException();
        }
        Duration ttl = Duration.ofSeconds(s3Properties.presigned().defaultExpirationSeconds());
        URL url = fileStoragePort.generatePresignedUrl(member.getProfileImageKey(), ttl);
        return new ProfileImageUrlResponse(url.toString(), LocalDateTime.now().plus(ttl));
    }

    private Member findOrThrow(Long id) {
        return memberRepository.findById(id).orElseThrow(MemberNotFoundException::new);
    }

    private String generateKey(Long memberId, String originalFilename) {
        String ext = extractExtension(originalFilename);
        return "profile/%d/%s.%s".formatted(memberId, UUID.randomUUID(), ext);
    }

    private String extractExtension(String filename) {
        if (filename == null) {
            return "bin";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "bin";
        }
        return filename.substring(dot + 1);
    }
}
