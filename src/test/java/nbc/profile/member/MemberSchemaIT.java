package nbc.profile.member;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import nbc.profile.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MemberSchemaIT {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("MEMBER 엔티티가 JPA metamodel에 등록되고 필수 컬럼을 모두 가진다 (AC1 자동화)")
    void member_테이블_자동생성_검증() {
        EntityType<Member> entityType = entityManager.getMetamodel().entity(Member.class);

        assertThat(entityType).isNotNull();
        assertThat(entityType.getAttributes())
                .extracting(Attribute::getName)
                .contains("id", "name", "age", "mbti", "profileImageKey", "createdAt", "updatedAt");
    }
}
