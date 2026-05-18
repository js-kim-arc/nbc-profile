package nbc.profile.shared.application.port.out;

import nbc.profile.common.exception.BusinessException;
import nbc.profile.common.exception.ErrorCode;

public class FileStorageException extends BusinessException {

    public FileStorageException(ErrorCode errorCode) {
        super(errorCode);
    }

    public FileStorageException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
