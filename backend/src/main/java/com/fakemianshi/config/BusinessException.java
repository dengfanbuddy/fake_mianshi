package com.fakemianshi.config;

/**
 * 业务异常：用于业务规则校验失败时抛出，由 GlobalExceptionHandler 统一处理。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
