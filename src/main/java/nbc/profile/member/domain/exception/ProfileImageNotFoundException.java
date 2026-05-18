package nbc.profile.member.domain.exception;

import nbc.profile.common.exception.BusinessException;
import nbc.profile.common.exception.ErrorCode;

public class ProfileImageNotFoundException extends BusinessException {

    public ProfileImageNotFoundException() {
        super(ErrorCode.PROFILE_IMAGE_NOT_FOUND);
    }
}
