package nbc.profile.shared.infrastructure.storage;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(S3StorageProperties.class)
public class S3StorageConfig {

    @Bean
    public S3Client s3Client(S3StorageProperties props) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(resolveCredentials(props));
        if (hasEndpoint(props)) {
            builder.endpointOverride(URI.create(props.endpoint()));
            builder.forcePathStyle(true);
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(S3StorageProperties props) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(resolveCredentials(props));
        if (hasEndpoint(props)) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }

    private boolean hasEndpoint(S3StorageProperties props) {
        return props.endpoint() != null && !props.endpoint().isBlank();
    }

    private AwsCredentialsProvider resolveCredentials(S3StorageProperties props) {
        if (props.credentials() != null && props.credentials().isStatic()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                            props.credentials().accessKey(),
                            props.credentials().secretKey()));
        }
        return DefaultCredentialsProvider.create();
    }
}
