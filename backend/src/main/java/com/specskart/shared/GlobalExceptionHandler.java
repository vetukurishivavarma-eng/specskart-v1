package com.specskart.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        String traceId = UUID.randomUUID().toString();
        log.warn("api-error code={} traceId={} msg={}", ex.getCode(), traceId, ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getCode(), ex.getMessage(), traceId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String traceId = UUID.randomUUID().toString();
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_FAILED", msg, traceId));
    }

    /**
     * A path or query value that isn't the type the route declares -- "/lens/not-a-uuid". That is
     * the caller's mistake, not ours, and it used to fall through to the catch-all below: a 500,
     * plus a stack trace at ERROR level. Staff order links get tapped out of WhatsApp, where a
     * wrapped or truncated URL is routine, so this would have buried real errors under noise.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String traceId = UUID.randomUUID().toString();
        log.warn("bad-parameter name={} traceId={}", ex.getName(), traceId);
        return ResponseEntity.badRequest()
                .body(ApiError.of("BAD_PARAMETER", "'" + ex.getName() + "' is not in the expected format.", traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        String traceId = UUID.randomUUID().toString();
        log.error("unexpected-error traceId={}", traceId, ex);
        return ResponseEntity.status(500)
                .body(ApiError.of("INTERNAL_ERROR", "Something went wrong.", traceId));
    }
}
