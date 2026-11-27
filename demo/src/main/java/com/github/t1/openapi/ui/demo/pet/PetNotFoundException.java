package com.github.t1.openapi.ui.demo.pet;

import com.github.t1.openapi.ui.demo.api.BusinessException;

class PetNotFoundException extends BusinessException {
    PetNotFoundException(long id) { super("Pet with ID " + id + " not found"); }
}
