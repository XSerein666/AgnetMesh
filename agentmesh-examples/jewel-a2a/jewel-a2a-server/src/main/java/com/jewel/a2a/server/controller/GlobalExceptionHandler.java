package com.jewel.a2a.server.controller;

import com.agentmesh.core.infrastructure.AgentMeshException;
import com.jewel.a2a.common.exception.A2AException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理参数校验异常、业务异常等，避免敏感信息泄露到响应中
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 请求体参数校验异常（@Valid 触发）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", errors);

        return buildResponse(HttpStatus.BAD_REQUEST, "参数校验失败: " + errors);
    }

    /**
     * 路径参数/查询参数校验异常（@Validated 触发）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("约束校验失败: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "参数校验失败: " + ex.getMessage());
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(AgentMeshException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(AgentMeshException ex) {
        log.warn("业务异常: code={}, message={}", ex.getCode(), ex.getMessage());
        return buildResponse(HttpStatus.valueOf(ex.getCode()), ex.getMessage());
    }

    /**
     * A2A 业务异常
     */
    @ExceptionHandler(A2AException.class)
    public ResponseEntity<Map<String, Object>> handleA2AException(A2AException ex) {
        log.warn("A2A业务异常: code={}, message={}", ex.getCode(), ex.getMessage());
        return buildResponse(HttpStatus.valueOf(ex.getCode()), ex.getMessage());
    }

    /**
     * 静态资源未找到（如 favicon.ico），不记录错误日志，避免日志噪音
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("静态资源未找到: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "资源未找到");
    }

    /**
     * 未捕获异常（兜底，不暴露内部错误详情）
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(Exception ex) {
        log.error("未捕获异常", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误，请稍后重试");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
