package dev.persefonia.webadmin;

/** Minimal shared rendering contract; validation remains owned by each form. */
public interface FieldError {
    String field();
    String message();
}
