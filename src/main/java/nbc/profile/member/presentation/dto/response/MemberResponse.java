package nbc.profile.member.presentation.dto.response;

import java.time.LocalDateTime;
import nbc.profile.member.domain.Member;

public record MemberResponse(
        Long id,
        String name,
        int age,
        String mbti,
        String profileImageKey,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getName(),
                member.getAge(),
                member.getMbti().name(),
                member.getProfileImageKey(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }
}
