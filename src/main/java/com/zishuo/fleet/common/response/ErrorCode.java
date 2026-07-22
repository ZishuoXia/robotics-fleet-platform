package com.zishuo.fleet.common.response;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Centralized catalogue of API error codes. Every error returned to a client
 * references one of these stable codes, so clients branch on a machine-readable
 * identifier rather than parsing English messages.
 */
@Getter
public enum ErrorCode {

    // ----- Generic -----
    OK("OK", "Success", HttpStatus.OK),
    BAD_REQUEST("BAD_REQUEST", "Invalid request", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "Authentication required", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "Permission denied", HttpStatus.FORBIDDEN),
    NOT_FOUND("NOT_FOUND", "Resource not found", HttpStatus.NOT_FOUND),
    CONFLICT("CONFLICT", "Resource conflict", HttpStatus.CONFLICT),
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_FAILED("VALIDATION_FAILED", "Request validation failed", HttpStatus.BAD_REQUEST),

    // ----- Auth domain -----
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS",
            "Invalid username or password", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_EXPIRED("AUTH_TOKEN_EXPIRED", "Token has expired", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_INVALID("AUTH_TOKEN_INVALID", "Token is invalid", HttpStatus.UNAUTHORIZED),
    AUTH_USERNAME_TAKEN("AUTH_USERNAME_TAKEN", "Username is already taken", HttpStatus.CONFLICT),
    AUTH_TENANT_NOT_FOUND("AUTH_TENANT_NOT_FOUND", "Tenant does not exist", HttpStatus.NOT_FOUND),
    AUTH_USER_DISABLED("AUTH_USER_DISABLED", "User account is disabled", HttpStatus.FORBIDDEN),

    // ----- Device domain -----
    DEVICE_NOT_FOUND("DEVICE_NOT_FOUND", "Device not found", HttpStatus.NOT_FOUND),
    DEVICE_SERIAL_TAKEN("DEVICE_SERIAL_TAKEN",
            "A device with this serial number already exists", HttpStatus.CONFLICT),
    DEVICE_TENANT_MISMATCH("DEVICE_TENANT_MISMATCH",
            "Device does not belong to your tenant", HttpStatus.FORBIDDEN);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }
}
