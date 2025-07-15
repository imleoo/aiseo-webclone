package com.jiwu.aiseo.siteclone.exception;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unexpected error occurred", e);
        // 不暴露详细的异常信息给客户端
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred. Please try again later.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Invalid argument: {}", e.getMessage());
        return createErrorResponse(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(SecurityException e) {
        log.warn("Security violation: {}", e.getMessage());
        return createErrorResponse(HttpStatus.FORBIDDEN, "Access denied", "Request denied for security reasons");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException e) {
        log.error("Runtime error occurred: {}", e.getMessage(), e);
        // 根据异常类型返回不同的错误信息
        String userMessage = getUserFriendlyMessage(e);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Runtime error", userMessage);
    }

    private String getUserFriendlyMessage(RuntimeException e) {
        String message = e.getMessage();
        if (message == null) {
            return "An error occurred while processing your request";
        }
        
        // 过滤掉可能包含敏感信息的异常消息
        if (message.contains("SQLException") || message.contains("database") || 
            message.contains("connection") || message.contains("authentication")) {
            return "A data processing error occurred";
        }
        
        if (message.contains("Failed to create download directory")) {
            return "Unable to create download directory";
        }
        
        if (message.contains("Invalid URL") || message.contains("Unsafe domain")) {
            return message; // 这些是安全的用户错误信息
        }
        
        // 默认返回通用错误信息
        return "An error occurred while processing your request";
    }

    private ResponseEntity<ErrorResponse> createErrorResponse(HttpStatus status, String error, String message) {
        ErrorResponse response = new ErrorResponse();
        response.setTimestamp(LocalDateTime.now());
        response.setStatus(status.value());
        response.setError(error);
        response.setMessage(message);
        return new ResponseEntity<>(response, status);
    }

    @Data
    public static class ErrorResponse {
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String message;
    }
}
