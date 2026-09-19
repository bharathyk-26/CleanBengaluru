package com.cleanbengaluru.exception;

import com.cleanbengaluru.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

/**
 * One place that turns every exception into the same JSON shape:
 *
 * { "success": false, "message": "...", "status": 404, "path": "...", "timestamp": "..." }
 *
 * @RestControllerAdvice makes these handlers apply to every controller in the app.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return build(ex.getMessage(), HttpStatus.NOT_FOUND, req);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest req) {
        return build(ex.getMessage(), HttpStatus.BAD_REQUEST, req);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateResourceException ex, HttpServletRequest req) {
        return build(ex.getMessage(), HttpStatus.CONFLICT, req);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleTransition(InvalidStatusTransitionException ex, HttpServletRequest req) {
        return build(ex.getMessage(), HttpStatus.CONFLICT, req);
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ErrorResponse> handleFile(FileStorageException ex, HttpServletRequest req) {
        return build(ex.getMessage(), HttpStatus.BAD_REQUEST, req);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return build("Uploaded file is too large. Maximum allowed size is 5MB.",
                HttpStatus.PAYLOAD_TOO_LARGE, req);
    }

    /** @Valid failures on a request body / form. Collects every field error at once. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("Validation failed", 400, req.getRequestURI(), fieldErrors));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest req) {
        return build("Invalid email or password", HttpStatus.UNAUTHORIZED, req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build("You do not have permission to perform this action", HttpStatus.FORBIDDEN, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build("Malformed JSON request body", HttpStatus.BAD_REQUEST, req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex,
                                                            HttpServletRequest req) {
        return build("Missing required parameter: " + ex.getParameterName(), HttpStatus.BAD_REQUEST, req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest req) {
        return build("Invalid value for parameter: " + ex.getName(), HttpStatus.BAD_REQUEST, req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest req) {
        log.error("Database constraint violation", ex);
        return build("Database constraint violation. The record may already exist or be in use.",
                HttpStatus.CONFLICT, req);
    }

    /** Last resort. Never leak the stack trace to the client — log it instead. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception at {}", req.getRequestURI(), ex);
        return build("Something went wrong. Please try again later.",
                HttpStatus.INTERNAL_SERVER_ERROR, req);
    }

    private ResponseEntity<ErrorResponse> build(String message, HttpStatus status, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(message, status.value(), req.getRequestURI()));
    }
}
