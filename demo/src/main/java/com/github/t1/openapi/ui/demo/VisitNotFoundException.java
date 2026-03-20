package com.github.t1.openapi.ui.demo;

class VisitNotFoundException extends BusinessException {
    VisitNotFoundException(long id) { super("Visit with ID " + id + " not found"); }
}
