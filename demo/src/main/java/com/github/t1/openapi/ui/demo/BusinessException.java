package com.github.t1.openapi.ui.demo;

public abstract class BusinessException extends RuntimeException {
    BusinessException(String message) { super(message); }
}
