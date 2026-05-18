package nbc.profile.shared.infrastructure.storage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.storage.s3")
@Validated
public record S3StorageProperties(
        @NotBlank String bucket,
        @NotBlank String region,
        @URL String endpoint,
        @Valid Credentials credentials,
        @Valid Presigned presigned
) {

    public record Credentials(String accessKey, String secretKey) {
        public boolean isStatic() {
            return accessKey != null && !accessKey.isBlank()
                    && secretKey != null && !secretKey.isBlank();
        }
    }

    public record Presigned(@Min(60) @Max(604800) long defaultExpirationSeconds) {
    }
}
