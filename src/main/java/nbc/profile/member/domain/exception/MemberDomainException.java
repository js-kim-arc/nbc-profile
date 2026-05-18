package nbc.profile.member.domain.exception;

import nbc.profile.common.exception.BusinessException;
import nbc.profile.common.exception.ErrorCode;

public class MemberDomainException extends BusinessException {

    public MemberDomainException(ErrorCode errorCode) {
        super(errorCode);
    }

    public MemberDomainException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
