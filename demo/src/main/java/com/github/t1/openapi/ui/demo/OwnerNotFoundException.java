package com.github.t1.openapi.ui.demo;

class OwnerNotFoundException extends BusinessException {
    OwnerNotFoundException(long id) { super("Owner with ID " + id + " not found"); }
}
