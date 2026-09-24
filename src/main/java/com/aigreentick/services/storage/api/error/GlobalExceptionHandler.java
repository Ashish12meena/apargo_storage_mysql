package com.aigreentick.services.storage.api.error;

import com.aigreentick.services.storage.api.common.dto.response.ApiFieldError;
import com.aigreentick.services.storage.api.common.dto.response.ApiResponse;
import com.aigreentick.services.storage.common.context.RequestContext;
import com.aigreentick.services.storage.common.error.ErrorCode;
import com.aigreentick.services.storage.common.error.FieldErrorCode;
import com.aigreentick.services.storage.domain.exception.DomainException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.Errors;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Exception → HTTP, in the standard error wrapper (API Standard §6). The single
 * place a status code is chosen.
 *
 * <ul>
 *   <li><b>{@link DomainException}</b> — every one carries an {@link ErrorCode},
 *       and the code carries its HTTP status, so one handler renders them all.
 *       Client text is {@link DomainException#clientMessage()}; the internal
 *       message goes only to the log (the predecessor leaked storage keys in
 *       404 bodies by echoing {@code getMessage()}).</li>
 *   <li><b>400 {@code BAD_REQUEST}</b> — the request can't be read: unparseable
 *       body, missing or unusable header.</li>
 *   <li><b>422 {@code VALIDATION_FAILED}</b> — the request was read but a field
 *       is invalid; details in {@code errors[]} with standard field codes.</li>
 * </ul>
 *
 * <p>Full mapping table: docs/10-error-handling.md §3.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Codes that signal an attack or a broken client: WARN, alertable above a rate threshold. */
    private static final Set<ErrorCode> WARN_CODES = Set.of(
            ErrorCode.FORBIDDEN, ErrorCode.CONTENT_TYPE_MISMATCH, ErrorCode.IDEMPOTENCY_KEY_REUSED);

    // ── Domain ─────────────────────────────────────────────────────────────

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException ex, HttpServletRequest request) {
        ErrorCode code = ex.errorCode();
        if (code.httpStatus() >= 500) {
            log.error("{} [{}]: {}", code, code.httpStatus(), ex.getMessage(), ex);
        } else if (WARN_CODES.contains(code)) {
            log.warn("{} [{}]: {}", code, code.httpStatus(), ex.getMessage());
        } else {
            log.info("{} [{}]: {}", code, code.httpStatus(), ex.getMessage());
        }

        HttpHeaders headers = new HttpHeaders();
        if (code == ErrorCode.IDEMPOTENCY_KEY_IN_PROGRESS) {
            headers.set(HttpHeaders.RETRY_AFTER, "2");
        } else if (code == ErrorCode.SERVICE_UNAVAILABLE) {
            headers.set(HttpHeaders.RETRY_AFTER, "5");
        }
        return ResponseEntity.status(code.httpStatus()).headers(headers)
                .body(envelope(code, ex.clientMessage(), List.of(), request));
    }

    // ── 422: field validation ──────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiFieldError> errors = toFieldErrors(ex.getBindingResult());
        log.debug("body validation failed: {} field(s)", errors.size());
        return validationFailed(errors, request);
    }

    /**
     * Built-in method validation on headers, query parameters, path variables
     * and — when those carry constraints — the {@code @Valid} body. Any bad
     * header makes the whole response 400; otherwise 422 with field errors.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {

        List<String> headerProblems = new ArrayList<>();
        List<ApiFieldError> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bodyErrors) {
                errors.addAll(toFieldErrors(bodyErrors));
                continue;
            }
            MethodParameter parameter = result.getMethodParameter();
            RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                if (header != null) {
                    headerProblems.add("'" + headerName(header, parameter) + "' " + error.getDefaultMessage());
                } else {
                    errors.add(new ApiFieldError(requestName(parameter),
                            FieldErrorCode.fromCodes(error.getCodes()).name(), error.getDefaultMessage()));
                }
            }
        }
        if (!headerProblems.isEmpty()) {
            String message = "Invalid header " + String.join("; ", headerProblems);
            log.warn("{}", message);
            return error(ErrorCode.BAD_REQUEST, message, request);
        }
        log.debug("parameter validation failed: {} error(s)", errors.size());
        return validationFailed(errors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiFieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new ApiFieldError(leafName(v), FieldErrorCode.fromConstraint(
                        v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()).name(),
                        v.getMessage()))
                .toList();
        return validationFailed(errors, request);
    }

    /**
     * {@code required = false} is used for the batch {@code files} part so the use
     * case can answer {@code BATCH_FILES_REQUIRED}; every other missing part
     * lands here.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(
            MissingServletRequestPartException ex, HttpServletRequest request) {
        log.warn("missing multipart part '{}' — check the form-data field name and that "
                + "Content-Type carries a boundary", ex.getRequestPartName());
        return validationFailed(List.of(new ApiFieldError(ex.getRequestPartName(),
                FieldErrorCode.REQUIRED.name(), ex.getRequestPartName() + " is required")), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("missing required parameter '{}'", ex.getParameterName());
        return validationFailed(List.of(new ApiFieldError(ex.getParameterName(),
                FieldErrorCode.REQUIRED.name(), ex.getParameterName() + " is required")), request);
    }

    /** Headers are 400 (the request can't be used); query and path values are 422. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        MethodParameter parameter = ex.getParameter();
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        log.warn("parameter '{}' could not be converted from value [{}]", ex.getName(), ex.getValue());
        if (header != null) {
            return error(ErrorCode.BAD_REQUEST,
                    "Invalid header '" + headerName(header, parameter) + "'", request);
        }
        String name = requestName(parameter);
        Class<?> type = ex.getRequiredType();
        String message = type != null && type.isEnum()
                ? name + " must be one of " + Arrays.toString(type.getEnumConstants())
                : name + " has an invalid value";
        return validationFailed(List.of(new ApiFieldError(name, FieldErrorCode.INVALID_VALUE.name(), message)),
                request);
    }

    // ── 400: unreadable request ────────────────────────────────────────────

    /** Unparseable JSON is 400; a wrong-typed field in valid JSON is a 422 on that field. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("unreadable request body: {}", ex.getMostSpecificCause().toString());
        if (ex.getCause() instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
            String field = jsonPath(mie.getPath());
            Class<?> target = mie.getTargetType();
            String message = mie instanceof InvalidFormatException && target != null && target.isEnum()
                    ? field + " must be one of " + Arrays.toString(target.getEnumConstants())
                    : field + " has an invalid value";
            return validationFailed(List.of(new ApiFieldError(field, FieldErrorCode.INVALID_VALUE.name(), message)),
                    request);
        }
        return error(ErrorCode.BAD_REQUEST, "Request body is missing or is not valid JSON.", request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {
        log.warn("missing required header '{}'", ex.getHeaderName());
        return error(ErrorCode.BAD_REQUEST, "Missing required header '" + ex.getHeaderName() + "'", request);
    }

    /**
     * A RAW {@code IllegalArgumentException}: a domain value object
     * ({@code MediaId}, {@code TenantRef}, {@code ByteSize}, ...) rejected input
     * that should have been validated earlier. 400 to the caller; ERROR with the
     * stack trace to the log, because the trace names the missing guard.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.error("unguarded IllegalArgumentException [req={}]: {}",
                RequestContext.requestIdOrNull(), ex.getMessage(), ex);
        return error(ErrorCode.BAD_REQUEST, ErrorCode.BAD_REQUEST.defaultMessage(), request);
    }

    // ── Protocol-level ─────────────────────────────────────────────────────

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoRoute(Exception ex, HttpServletRequest request) {
        log.debug("no handler for {} {}", request.getMethod(), request.getRequestURI());
        return error(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        if (ex.getSupportedHttpMethods() != null) {
            headers.setAllow(ex.getSupportedHttpMethods());
        }
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.httpStatus()).headers(headers)
                .body(envelope(ErrorCode.METHOD_NOT_ALLOWED,
                        ErrorCode.METHOD_NOT_ALLOWED.defaultMessage(), List.of(), request));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return error(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type '" + ex.getContentType() + "' is not supported; use " + ex.getSupportedMediaTypes(),
                request);
    }

    /** Container multipart ceiling (max-file-size / max-request-size). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.debug("multipart limit exceeded: {}", ex.getMessage());
        return error(ErrorCode.MEDIA_TOO_LARGE, ErrorCode.MEDIA_TOO_LARGE.defaultMessage(), request);
    }

    /**
     * Safety net. A remaining Spring MVC exception already knows its 4xx status;
     * anything else is a 500 with a generic message — an exception message can
     * hold a storage key, a path or SQL.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse springError && springError.getStatusCode().is4xxClientError()) {
            HttpStatusCode reported = springError.getStatusCode();
            ErrorCode code = ErrorCode.forStatus(reported.value());
            HttpStatus resolved = HttpStatus.resolve(reported.value());
            int status = resolved != null ? resolved.value() : code.httpStatus();
            log.warn("{} on {}: {}", status, request.getRequestURI(), ex.getMessage());
            return ResponseEntity.status(status).body(ApiResponse.error(status, code.name(),
                    resolved != null ? resolved.getReasonPhrase() : code.defaultMessage(),
                    List.of(), request.getRequestURI()));
        }
        log.error("unexpected error", ex);
        return error(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), request);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private ResponseEntity<ApiResponse<Void>> validationFailed(List<ApiFieldError> errors, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus()).body(envelope(
                ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors, request));
    }

    private ResponseEntity<ApiResponse<Void>> error(ErrorCode code, String message, HttpServletRequest request) {
        return ResponseEntity.status(code.httpStatus()).body(envelope(code, message, List.of(), request));
    }

    private ApiResponse<Void> envelope(ErrorCode code, String message, List<ApiFieldError> errors,
                                       HttpServletRequest request) {
        return ApiResponse.error(code.httpStatus(), code.name(), message, errors, request.getRequestURI());
    }

    private List<ApiFieldError> toFieldErrors(Errors errors) {
        List<ApiFieldError> result = new ArrayList<>();
        errors.getFieldErrors().forEach(fe -> result.add(new ApiFieldError(
                fe.getField(), FieldErrorCode.fromConstraint(fe.getCode()).name(), fe.getDefaultMessage())));
        errors.getGlobalErrors().forEach(ge -> result.add(new ApiFieldError(
                ge.getObjectName(), FieldErrorCode.fromConstraint(ge.getCode()).name(), ge.getDefaultMessage())));
        return result;
    }

    private String requestName(MethodParameter parameter) {
        RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
        if (param != null) {
            String declared = firstNonBlank(param.name(), param.value());
            if (declared != null) {
                return declared;
            }
        }
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        if (path != null) {
            String declared = firstNonBlank(path.name(), path.value());
            if (declared != null) {
                return declared;
            }
        }
        String name = parameter.getParameterName();
        return name != null ? name : "parameter";
    }

    private String headerName(RequestHeader header, MethodParameter parameter) {
        String declared = firstNonBlank(header.name(), header.value());
        return declared != null ? declared : String.valueOf(parameter.getParameterName());
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null && !b.isBlank() ? b : null;
    }

    private String jsonPath(List<JsonMappingException.Reference> path) {
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference ref : path) {
            if (ref.getFieldName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getFieldName());
            } else {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    private String leafName(ConstraintViolation<?> violation) {
        String leaf = null;
        for (Path.Node node : violation.getPropertyPath()) {
            leaf = node.getName();
        }
        return leaf != null ? leaf : "parameter";
    }
}
