package com.github.t1.openapi.ui.demo.owner;

import com.github.t1.openapi.ui.demo.api.BusinessException;

class OwnerNotFoundException extends BusinessException {
    OwnerNotFoundException(long id) { super("Owner with ID " + id + " not found"); }
}
