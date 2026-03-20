package com.github.t1.openapi.ui.demo;

class InvalidOwnerIdException extends BusinessException {
    InvalidOwnerIdException(long id) { super("Owner with ID " + id + " does not exist"); }
}
