package nbc.profile.shared.infrastructure.storage;

import java.net.URL;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nbc.profile.common.exception.ErrorCode;
import nbc.profile.shared.application.port.out.FileStorageException;
import nbc.profile.shared.application.port.out.FileStoragePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * S3-backed implementation of {@link FileStoragePort}.
 *
 * <p>Usage example (from a future Application Service — not yet wired):
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * class MemberProfileService {
 *     private final FileStoragePort fileStorage;
 *     private final MemberRepository memberRepository;
 *
 *     public void updateProfileImage(Long memberId, byte[] bytes, String contentType) {
 *         String key = "profile/%d/%s".formatted(memberId, UUID.randomUUID());
 *         fileStorage.upload(key, bytes, contentType);
 *         Member member = memberRepository.findById(memberId).orElseThrow();
 *         member.updateProfileImageKey(key);
 *     }
 * }
 * }</pre>
 *
 * <p>로깅 정책: 업로드/삭제 시 key + size 만 기록한다. content payload 는 절대 로깅하지 않는다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class S3FileStorageAdapter implements FileStoragePort {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3StorageProperties props;

    @Override
    public URL upload(String key, byte[] content, String contentType) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(props.bucket())
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));
            log.info("S3 upload ok: bucket={}, key={}, size={}",
                    props.bucket(), key, content.length);
            return s3Client.utilities().getUrl(
                    GetUrlRequest.builder().bucket(props.bucket()).key(key).build());
        } catch (S3Exception e) {
            throw new FileStorageException(ErrorCode.FILE_STORAGE_UPLOAD_FAILED, e);
        }
    }

    @Override
    public byte[] download(String key) {
        try {
            return s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(props.bucket()).key(key).build()
            ).asByteArray();
        } catch (NoSuchKeyException e) {
            throw new FileStorageException(ErrorCode.FILE_STORAGE_NOT_FOUND, e);
        } catch (S3Exception e) {
            throw new FileStorageException(ErrorCode.FILE_STORAGE_DOWNLOAD_FAILED, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder().bucket(props.bucket()).key(key).build());
            log.info("S3 delete ok: bucket={}, key={}", props.bucket(), key);
        } catch (S3Exception e) {
            throw new FileStorageException(ErrorCode.FILE_STORAGE_DELETE_FAILED, e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            s3Client.headObject(
                    HeadObjectRequest.builder().bucket(props.bucket()).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            throw new FileStorageException(ErrorCode.FILE_STORAGE_DOWNLOAD_FAILED, e);
        }
    }

    @Override
    public URL generatePresignedUrl(String key, Duration expiration) {
        try {
            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(
                    GetObjectPresignRequest.builder()
                            .signatureDuration(expiration)
                            .getObjectRequest(GetObjectRequest.builder()
                                    .bucket(props.bucket()).key(key).build())
                            .build());
            return presigned.url();
        } catch (S3Exception e) {
            throw new FileStorageException(ErrorCode.FILE_STORAGE_PRESIGN_FAILED, e);
        }
    }
}
