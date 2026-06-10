package dev.persefonia.app.security.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationException;

@ControllerAdvice
public final class AdminCommandAuthorizationExceptionHandler {
    @ExceptionHandler(AdminCommandAuthorizationException.class)
    public ResponseEntity<Void> handle(AdminCommandAuthorizationException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
