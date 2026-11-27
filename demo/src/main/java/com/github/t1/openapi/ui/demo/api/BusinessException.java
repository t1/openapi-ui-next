package com.github.t1.openapi.ui.demo.api;

public abstract class BusinessException extends RuntimeException {
    public BusinessException(String message) { super(message); }
}
