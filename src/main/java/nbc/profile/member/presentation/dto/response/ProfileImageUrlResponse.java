package nbc.profile.member.presentation.dto.response;

import java.time.LocalDateTime;

public record ProfileImageUrlResponse(String imageUrl, LocalDateTime expiresAt) {
}
