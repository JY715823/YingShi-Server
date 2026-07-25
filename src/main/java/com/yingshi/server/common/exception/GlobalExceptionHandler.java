package com.yingshi.server.common.exception;

import com.yingshi.server.common.response.ApiError;
import com.yingshi.server.common.response.ApiResponse;
import com.yingshi.server.config.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(
            ApiException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                request,
                exception.getStatus(),
                exception.getErrorCode(),
                exception.getMessage(),
                exception.getDetails()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<Map<String, String>> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toValidationDetail)
                .toList();

        return buildErrorResponse(
                request,
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Request validation failed.",
                details
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<Map<String, String>> details = exception.getConstraintViolations()
                .stream()
                .map(violation -> Map.of(
                        "field", violation.getPropertyPath().toString(),
                        "message", violation.getMessage()
                ))
                .toList();

        return buildErrorResponse(
                request,
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Request validation failed.",
                details
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                request,
                HttpStatus.METHOD_NOT_ALLOWED,
                ErrorCode.METHOD_NOT_ALLOWED,
                exception.getMessage(),
                null
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                request,
                HttpStatus.NOT_FOUND,
                ErrorCode.NOT_FOUND,
                "Requested resource was not found.",
                Map.of("path", request.getRequestURI())
        );
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        String rootMsg = exception.getMostSpecificCause() != null
                ? exception.getMostSpecificCause().getMessage()
                : exception.getMessage();
        log.warn("Data integrity violation for {} {}: {}", request.getMethod(), request.getRequestURI(), rootMsg, exception);
        return buildErrorResponse(
                request,
                HttpStatus.CONFLICT,
                ErrorCode.VALIDATION_ERROR,
                "Data integrity violation: " + rootMsg,
                null
        );
    }

    @ExceptionHandler(org.springframework.jdbc.BadSqlGrammarException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadSqlGrammar(
            org.springframework.jdbc.BadSqlGrammarException exception,
            HttpServletRequest request
    ) {
        // 常见原因：H2 ddl-auto=update 未追上实体变更（缺列/列类型不匹配），或 Flyway 迁移未应用
        String rootMsg = exception.getMostSpecificCause() != null
                ? exception.getMostSpecificCause().getMessage()
                : exception.getMessage();
        log.error("Bad SQL grammar for {} {}: {}", request.getMethod(), request.getRequestURI(), rootMsg, exception);
        return buildErrorResponse(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.SERVER_ERROR,
                "Database schema mismatch: " + exception.getClass().getSimpleName() + " - " + rootMsg,
                null
        );
    }

    @ExceptionHandler(jakarta.persistence.PersistenceException.class)
    public ResponseEntity<ApiResponse<Void>> handlePersistenceException(
            jakarta.persistence.PersistenceException exception,
            HttpServletRequest request
    ) {
        // Hibernate 包装的异常（包括 ConstraintViolationException、JDBCException 等）
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String rootMsg = root.getMessage();
        log.error("Persistence error for {} {}: {}", request.getMethod(), request.getRequestURI(), rootMsg, exception);
        // 如果根因是 DataIntegrityViolation，返回 409
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorCode code = ErrorCode.SERVER_ERROR;
        if (root instanceof org.springframework.dao.DataIntegrityViolationException
                || root instanceof org.hibernate.exception.ConstraintViolationException) {
            status = HttpStatus.CONFLICT;
            code = ErrorCode.VALIDATION_ERROR;
        }
        return buildErrorResponse(
                request,
                status,
                code,
                exception.getClass().getSimpleName() + ": " + rootMsg,
                null
        );
    }

    @ExceptionHandler(org.springframework.transaction.TransactionSystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleTransactionSystemException(
            org.springframework.transaction.TransactionSystemException exception,
            HttpServletRequest request
    ) {
        // 事务提交阶段抛出的异常（@Transactional 方法正常返回后 commit 时触发）
        // root cause 可能是 FK/NOT NULL 约束违反，但被 Spring 事务框架包装，
        // 不会进入 DataIntegrityViolationException 或 PersistenceException 处理器。
        // 必须独立处理，否则会被 generic Exception 处理器捕获并返回 500，
        // 导致客户端无法识别为约束违反并触发相应的回退策略。
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String rootMsg = root.getMessage();
        String rootType = root.getClass().getSimpleName();
        log.error("Transaction system error for {} {}: {} - {}",
                request.getMethod(), request.getRequestURI(), rootType, rootMsg, exception);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorCode code = ErrorCode.SERVER_ERROR;
        // 如果 root cause 是约束违反，返回 409 而非 500，让客户端按 4xx 路径处理
        if (root instanceof org.springframework.dao.DataIntegrityViolationException
                || root instanceof org.hibernate.exception.ConstraintViolationException
                || root instanceof java.sql.SQLIntegrityConstraintViolationException) {
            status = HttpStatus.CONFLICT;
            code = ErrorCode.VALIDATION_ERROR;
            log.warn("TransactionSystemException root cause is constraint violation, returning 409");
        }
        return buildErrorResponse(
                request,
                status,
                code,
                "Transaction error: " + rootType + " - " + rootMsg,
                null
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        // Hibernate / JPA 在枚举解析、字段映射失败时会抛出 IAE
        log.warn("Illegal argument for {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage(), exception);
        return buildErrorResponse(
                request,
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Invalid data: " + exception.getClass().getSimpleName() + " - " + exception.getMessage(),
                null
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        // 深入到根因，避免只看到 Spring 事务包装层
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String rootMsg = root.getMessage();
        String rootType = root.getClass().getSimpleName();
        log.error("Unexpected server error for {} {}: {} - {}", request.getMethod(), request.getRequestURI(), rootType, rootMsg, exception);
        return buildErrorResponse(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.SERVER_ERROR,
                "Server error: " + rootType + " - " + rootMsg,
                null
        );
    }

    private Map<String, String> toValidationDetail(FieldError fieldError) {
        return Map.of(
                "field", fieldError.getField(),
                "message", fieldError.getDefaultMessage() == null ? "Invalid value." : fieldError.getDefaultMessage()
        );
    }

    private ResponseEntity<ApiResponse<Void>> buildErrorResponse(
            HttpServletRequest request,
            HttpStatus status,
            ErrorCode errorCode,
            String message,
            Object details
    ) {
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        ApiError error = new ApiError(errorCode.name(), message, details);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(requestId, error));
    }
}
