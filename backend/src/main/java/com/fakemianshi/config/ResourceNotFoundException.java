package com.fakemianshi.config;

/**
 * 资源不存在异常：用于查询数据不存在时抛出，由 GlobalExceptionHandler 统一处理。
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
