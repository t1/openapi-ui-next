package com.github.t1.openapi.ui.demo.visit;

import com.github.t1.openapi.ui.demo.api.BusinessException;

class VisitNotFoundException extends BusinessException {
    VisitNotFoundException(long id) { super("Visit with ID " + id + " not found"); }
}
