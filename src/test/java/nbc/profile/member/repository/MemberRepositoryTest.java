package nbc.profile.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import nbc.profile.config.JpaAuditingConfig;
import nbc.profile.config.TestDateTimeProviderConfig;
import nbc.profile.member.domain.Mbti;
import nbc.profile.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@Import({JpaAuditingConfig.class, TestDateTimeProviderConfig.class})
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("save 후 findById는 같은 필드를 가진 객체를 반환한다")
    void save_그리고_findById_같은객체조회() {
        Member saved = memberRepository.save(Member.create("alice", 30, Mbti.INTJ));
        flushAndClear();

        Optional<Member> found = memberRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("alice");
        assertThat(found.get().getAge()).isEqualTo(30);
        assertThat(found.get().getMbti()).isEqualTo(Mbti.INTJ);
    }

    @Test
    @DisplayName("findById — 존재하지 않는 ID는 빈 Optional")
    void findById_존재하지않는ID_빈Optional() {
        Optional<Member> found = memberRepository.findById(9999L);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("save — createdAt·updatedAt이 자동 주입된다")
    void save_audit자동주입() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        Member saved = memberRepository.save(Member.create("alice", 30, Mbti.INTJ));
        flushAndClear();

        Member found = memberRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getCreatedAt()).isNotNull().isAfter(before);
        assertThat(found.getUpdatedAt()).isNotNull().isAfter(before);
    }

    @Test
    @DisplayName("save — profileImageKey가 null이어도 정상 저장")
    void save_profileImageKey_null_정상저장() {
        Member saved = memberRepository.save(Member.create("alice", 30, Mbti.INTJ));
        flushAndClear();

        Member found = memberRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getProfileImageKey()).isNull();
    }

    @Test
    @DisplayName("updateProfileImageKey — 값 변경 시 updatedAt이 갱신된다")
    void updateProfileImageKey_변경시_updatedAt갱신() {
        Member saved = memberRepository.save(Member.create("alice", 30, Mbti.INTJ));
        flushAndClear();
        LocalDateTime initialUpdatedAt = memberRepository.findById(saved.getId()).orElseThrow().getUpdatedAt();

        Member loaded = memberRepository.findById(saved.getId()).orElseThrow();
        loaded.updateProfileImageKey("k1");
        flushAndClear();

        Member after = memberRepository.findById(saved.getId()).orElseThrow();
        assertThat(after.getProfileImageKey()).isEqualTo("k1");
        assertThat(after.getUpdatedAt()).isAfter(initialUpdatedAt);
    }

    @Test
    @DisplayName("updateProfileImageKey — 동일 값 재호출 시 updatedAt 불변 (멱등성)")
    void updateProfileImageKey_동일값_updatedAt불변() {
        Member saved = memberRepository.save(Member.create("alice", 30, Mbti.INTJ));
        Member loaded = memberRepository.findById(saved.getId()).orElseThrow();
        loaded.updateProfileImageKey("k1");
        flushAndClear();
        LocalDateTime afterFirstUpdate = memberRepository.findById(saved.getId()).orElseThrow().getUpdatedAt();

        Member reloaded = memberRepository.findById(saved.getId()).orElseThrow();
        reloaded.updateProfileImageKey("k1");
        flushAndClear();

        Member finalState = memberRepository.findById(saved.getId()).orElseThrow();
        assertThat(finalState.getUpdatedAt()).isEqualTo(afterFirstUpdate);
    }

    @Test
    @DisplayName("save — 동일 이름 두 건이 모두 정상 저장된다 (name unique 아님)")
    void save_동일이름_둘다정상저장() {
        memberRepository.save(Member.create("alice", 30, Mbti.INTJ));
        memberRepository.save(Member.create("alice", 25, Mbti.ENFP));
        flushAndClear();

        List<Member> all = memberRepository.findAll();
        assertThat(all).hasSize(2).extracting(Member::getName).containsOnly("alice");
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
