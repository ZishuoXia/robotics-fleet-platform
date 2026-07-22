package com.zishuo.fleet.common.exception;

import com.zishuo.fleet.common.response.ErrorCode;
import lombok.Getter;

/**
 * Thrown for expected, recoverable business errors (e.g. "device not found",
 * "username taken"). Distinguished from unexpected runtime errors because we
 * expose the message and code to the client.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) { //使用默认消息, 标准化、通用的业务失败
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) { //使用自定义消息 需要精细化描述的业务失败
        super(message);
        this.errorCode = errorCode;
    }
}
