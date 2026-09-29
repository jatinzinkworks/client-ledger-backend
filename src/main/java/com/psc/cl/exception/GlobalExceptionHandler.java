package com.psc.cl.exception;

import com.psc.cl.dto.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Translates exceptions thrown anywhere in the service into a consistent {@link ErrorResponse}.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so the statuses Spring MVC already derives for
 * its own exceptions (405, 415, 404, …) are preserved and only the body is reshaped — a bare
 * catch-all would flatten all of them into 500s.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final String VALIDATION_MESSAGE = "One or more fields are invalid";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> ErrorResponse.FieldError.builder()
                        .field(error instanceof org.springframework.validation.FieldError fieldError
                                ? fieldError.getField()
                                : error.getObjectName())
                        .message(error.getDefaultMessage())
                        .build())
                .sorted(Comparator.comparing(ErrorResponse.FieldError::getField))
                .toList();

        log.warn("Validation failed for {}: {} field error(s)", path(request), fieldErrors.size());
        ErrorResponse body = body(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, VALIDATION_MESSAGE,
                request, fieldErrors);
        return ResponseEntity.badRequest().headers(headers).body(body);
    }

    /**
     * Reshapes every exception handled by the framework's own resolvers into {@link ErrorResponse},
     * keeping the status Spring MVC chose for it.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        log.warn("{} on {}: {}", status.value(), path(request), ex.getMessage());
        return ResponseEntity.status(status)
                .headers(headers)
                .body(body(status, status.name(), detail(ex, status), request, null));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                   WebRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> ErrorResponse.FieldError.builder()
                        .field(lastNode(violation))
                        .message(violation.getMessage())
                        .build())
                .sorted(Comparator.comparing(ErrorResponse.FieldError::getField))
                .toList();

        log.warn("Constraint violation on {}: {} violation(s)", path(request), fieldErrors.size());
        return ResponseEntity.badRequest()
                .body(body(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, VALIDATION_MESSAGE, request, fieldErrors));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        log.warn("Not found on {}: {}", path(request), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(body(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request, null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                                                      WebRequest request) {
        log.warn("Data integrity violation on {}", path(request), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body(HttpStatus.CONFLICT, "DATA_CONFLICT",
                        "The request conflicts with the current state of the data. Please retry.",
                        request, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error on {}", path(request), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                        "An unexpected error occurred. Please contact support if the problem persists.",
                        request, null));
    }

    private ErrorResponse body(HttpStatus status, String code, String message, WebRequest request,
                               List<ErrorResponse.FieldError> errors) {
        return ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .code(code)
                .message(message)
                .path(path(request))
                .errors(errors == null || errors.isEmpty() ? null : errors)
                .build();
    }

    /** Prefers the framework's own problem detail, falling back to the status reason phrase. */
    private String detail(Exception ex, HttpStatus status) {
        if (ex instanceof org.springframework.web.ErrorResponse errorResponse) {
            String detail = errorResponse.getBody().getDetail();
            if (detail != null && !detail.isBlank()) {
                return detail;
            }
        }
        return status.getReasonPhrase();
    }

    private String path(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : request.getDescription(false);
    }

    private String lastNode(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
    }
}
