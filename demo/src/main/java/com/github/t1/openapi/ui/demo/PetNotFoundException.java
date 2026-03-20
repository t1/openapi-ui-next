package com.github.t1.openapi.ui.demo;

class PetNotFoundException extends BusinessException {
    PetNotFoundException(long id) { super("Pet with ID " + id + " not found"); }
}
