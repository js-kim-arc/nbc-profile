package nbc.profile.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    MEMBER_NAME_BLANK(HttpStatus.BAD_REQUEST, "name must not be blank"),
    MEMBER_MBTI_NULL(HttpStatus.BAD_REQUEST, "mbti must not be null"),
    MEMBER_AGE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "age must be non-negative"),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "member not found"),
    PROFILE_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "profile image not registered"),

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "validation failed"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "file exceeds size limit"),

    FILE_STORAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "failed to upload file"),
    FILE_STORAGE_DOWNLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "failed to download file"),
    FILE_STORAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "file not found"),
    FILE_STORAGE_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "failed to delete file"),
    FILE_STORAGE_PRESIGN_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "failed to build presigned URL");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
