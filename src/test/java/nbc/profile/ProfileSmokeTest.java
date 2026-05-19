package nbc.profile;

import static org.assertj.core.api.Assertions.assertThat;

import nbc.profile.shared.application.port.out.FileStoragePort;
import nbc.profile.shared.infrastructure.storage.InMemoryFileStorageAdapter;
import nbc.profile.shared.infrastructure.storage.S3FileStorageAdapter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

class ProfileSmokeTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("local")
    class LocalProfile {

        @Autowired
        FileStoragePort fileStoragePort;

        @Test
        void contextLoads_localProfile_injectsS3Adapter() {
            assertThat(fileStoragePort).isInstanceOf(S3FileStorageAdapter.class);
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    class TestProfile {

        @Autowired
        FileStoragePort fileStoragePort;

        @Test
        void contextLoads_testProfile_injectsInMemoryAdapter() {
            assertThat(fileStoragePort).isInstanceOf(InMemoryFileStorageAdapter.class);
        }
    }
}
