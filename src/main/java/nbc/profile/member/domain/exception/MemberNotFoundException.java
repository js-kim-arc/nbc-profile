package nbc.profile.member.domain.exception;

import nbc.profile.common.exception.BusinessException;
import nbc.profile.common.exception.ErrorCode;

public class MemberNotFoundException extends BusinessException {

    public MemberNotFoundException() {
        super(ErrorCode.MEMBER_NOT_FOUND);
    }
}
