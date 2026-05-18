package nbc.profile.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nbc.profile.common.exception.ErrorCode;
import nbc.profile.member.domain.exception.MemberDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    @DisplayName("create — 정상 입력은 필드를 그대로 반영한다")
    void create_valid_엔티티생성() {
        Member member = Member.create("alice", 30, Mbti.INTJ);

        assertThat(member.getName()).isEqualTo("alice");
        assertThat(member.getAge()).isEqualTo(30);
        assertThat(member.getMbti()).isEqualTo(Mbti.INTJ);
        assertThat(member.getProfileImageKey()).isNull();
    }

    @Test
    @DisplayName("create — name이 blank/null이면 MEMBER_NAME_BLANK")
    void create_name_blank_예외() {
        assertThatThrownBy(() -> Member.create(null, 30, Mbti.INTJ))
                .isInstanceOf(MemberDomainException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NAME_BLANK);
        assertThatThrownBy(() -> Member.create("", 30, Mbti.INTJ))
                .isInstanceOf(MemberDomainException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NAME_BLANK);
        assertThatThrownBy(() -> Member.create("   ", 30, Mbti.INTJ))
                .isInstanceOf(MemberDomainException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NAME_BLANK);
    }

    @Test
    @DisplayName("create — name 앞뒤 공백은 trim 정규화")
    void create_name_공백포함_trim정규화() {
        Member member = Member.create("  alice  ", 30, Mbti.INTJ);

        assertThat(member.getName()).isEqualTo("alice");
    }

    @Test
    @DisplayName("create — mbti가 null이면 MEMBER_MBTI_NULL")
    void create_mbti_null_예외() {
        assertThatThrownBy(() -> Member.create("alice", 30, null))
                .isInstanceOf(MemberDomainException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_MBTI_NULL);
    }

    @Test
    @DisplayName("create — age가 음수면 MEMBER_AGE_OUT_OF_RANGE")
    void create_age_음수_예외() {
        assertThatThrownBy(() -> Member.create("alice", -1, Mbti.INTJ))
                .isInstanceOf(MemberDomainException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_AGE_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("create — age 0은 허용 (경계값)")
    void create_age_0_정상() {
        Member member = Member.create("alice", 0, Mbti.INTJ);

        assertThat(member.getAge()).isZero();
    }

    @Test
    @DisplayName("updateProfileImageKey — null 입력은 null 저장")
    void updateProfileImageKey_null_저장() {
        Member member = Member.create("alice", 30, Mbti.INTJ);

        member.updateProfileImageKey(null);

        assertThat(member.getProfileImageKey()).isNull();
    }

    @Test
    @DisplayName("updateProfileImageKey — 빈 문자열·공백은 null로 정규화")
    void updateProfileImageKey_blank_null정규화() {
        Member member = Member.create("alice", 30, Mbti.INTJ);
        member.updateProfileImageKey("k1");

        member.updateProfileImageKey("   ");

        assertThat(member.getProfileImageKey()).isNull();
    }

    @Test
    @DisplayName("updateProfileImageKey — 앞뒤 공백은 trim 정규화")
    void updateProfileImageKey_trim_정규화() {
        Member member = Member.create("alice", 30, Mbti.INTJ);

        member.updateProfileImageKey("  k1  ");

        assertThat(member.getProfileImageKey()).isEqualTo("k1");
    }

    @Test
    @DisplayName("updateProfileImageKey — 동일 값 호출은 no-op (멱등성)")
    void updateProfileImageKey_동일값_unchanged_노op() {
        Member member = Member.create("alice", 30, Mbti.INTJ);
        member.updateProfileImageKey("k1");

        member.updateProfileImageKey("k1");

        assertThat(member.getProfileImageKey()).isEqualTo("k1");
    }
}
