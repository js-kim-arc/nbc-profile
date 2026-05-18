package nbc.profile.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nbc.profile.common.exception.ErrorCode;
import nbc.profile.member.domain.exception.MemberDomainException;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int age;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Mbti mbti;

    @Column(name = "profile_image_key", length = 255)
    private String profileImageKey;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private Member(String name, int age, Mbti mbti) {
        this.name = name;
        this.age = age;
        this.mbti = mbti;
    }

    public static Member create(String name, int age, Mbti mbti) {
        String normalizedName = (name == null) ? null : name.trim();
        if (normalizedName == null || normalizedName.isEmpty()) {
            throw new MemberDomainException(ErrorCode.MEMBER_NAME_BLANK);
        }
        if (mbti == null) {
            throw new MemberDomainException(ErrorCode.MEMBER_MBTI_NULL);
        }
        if (age < 0) {
            throw new MemberDomainException(ErrorCode.MEMBER_AGE_OUT_OF_RANGE);
        }
        return new Member(normalizedName, age, mbti);
    }

    public void updateProfileImageKey(String key) {
        String normalized = (key == null) ? null : key.trim();
        if (normalized != null && normalized.isEmpty()) {
            normalized = null;
        }
        if (Objects.equals(this.profileImageKey, normalized)) {
            return;
        }
        this.profileImageKey = normalized;
    }
}
