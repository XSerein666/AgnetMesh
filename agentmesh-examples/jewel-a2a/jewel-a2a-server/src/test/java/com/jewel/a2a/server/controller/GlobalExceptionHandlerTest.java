package com.jewel.a2a.server.controller;

import com.agentmesh.core.infrastructure.AgentMeshException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GlobalExceptionHandler 单元测试：验证所有异常类型返回正确的 HTTP 状态码和响应体结构。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler 单元测试")
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    // ========== MethodArgumentNotValidException ==========

    @Nested
    @DisplayName("请求体参数校验异常")
    class MethodArgumentNotValid {

        @Test
        @DisplayName("应返回 400 和字段错误信息")
        void shouldReturn400WithFieldErrors() throws Exception {
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(null, "request");
            bindingResult.addError(new FieldError("request", "message", "消息不能为空"));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertEquals(400, body.get("status"));
            assertTrue(body.get("message").toString().contains("message"));
            assertTrue(body.get("message").toString().contains("消息不能为空"));
        }

        @Test
        @DisplayName("多个字段错误应合并显示")
        void shouldCombineMultipleFieldErrors() throws Exception {
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(null, "request");
            bindingResult.addError(new FieldError("request", "message", "不能为空"));
            bindingResult.addError(new FieldError("request", "sessionId", "格式不正确"));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            String message = body.get("message").toString();
            assertTrue(message.contains("message"));
            assertTrue(message.contains("sessionId"));
        }
    }

    // ========== ConstraintViolationException ==========

    @Nested
    @DisplayName("路径参数校验异常")
    class ConstraintViolation {

        @Test
        @DisplayName("应返回 400")
        void shouldReturn400() {
            ConstraintViolationException ex = mock(ConstraintViolationException.class);
            when(ex.getMessage()).thenReturn("taskId: 不能为空");

            ResponseEntity<Map<String, Object>> response = handler.handleConstraintViolation(ex);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertEquals(400, body.get("status"));
        }
    }

    // ========== AgentMeshException ==========

    @Nested
    @DisplayName("业务异常")
    class BusinessException {

        @Test
        @DisplayName("404 异常应返回 404")
        void shouldReturn404() {
            AgentMeshException ex = new AgentMeshException(404, "任务不存在");

            ResponseEntity<Map<String, Object>> response = handler.handleBusiness(ex);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals("任务不存在", response.getBody().get("message"));
        }

        @Test
        @DisplayName("500 异常应返回 500")
        void shouldReturn500() {
            AgentMeshException ex = new AgentMeshException(500, "内部错误");

            ResponseEntity<Map<String, Object>> response = handler.handleBusiness(ex);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals("内部错误", response.getBody().get("message"));
        }

        @Test
        @DisplayName("400 异常应返回 400")
        void shouldReturn400() {
            AgentMeshException ex = new AgentMeshException(400, "参数错误");

            ResponseEntity<Map<String, Object>> response = handler.handleBusiness(ex);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("参数错误", response.getBody().get("message"));
        }
    }

    // ========== NoResourceFoundException ==========

    @Nested
    @DisplayName("静态资源未找到")
    class NoResourceFound {

        @Test
        @DisplayName("应返回 404")
        void shouldReturn404() {
            NoResourceFoundException ex = mock(NoResourceFoundException.class);
            when(ex.getMessage()).thenReturn("No static resource favicon.ico");

            ResponseEntity<Map<String, Object>> response = handler.handleNoResourceFound(ex);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals("资源未找到", response.getBody().get("message"));
        }
    }

    // ========== Exception (兜底) ==========

    @Nested
    @DisplayName("未捕获异常兜底")
    class UnknownException {

        @Test
        @DisplayName("应返回 500 且不泄露内部错误详情")
        void shouldReturn500WithoutDetails() {
            RuntimeException ex = new RuntimeException("内部数据库连接失败");

            ResponseEntity<Map<String, Object>> response = handler.handleUnknown(ex);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertEquals(500, body.get("status"));
            assertEquals("服务器内部错误，请稍后重试", body.get("message"));
            // 不应泄露内部错误详情
            assertFalse(body.get("message").toString().contains("数据库"));
        }
    }

    // ========== 响应体结构 ==========

    @Nested
    @DisplayName("响应体结构")
    class ResponseStructure {

        @Test
        @DisplayName("应包含 timestamp、status、error、message 字段")
        void shouldContainAllRequiredFields() {
            AgentMeshException ex = new AgentMeshException(400, "测试错误");

            ResponseEntity<Map<String, Object>> response = handler.handleBusiness(ex);
            Map<String, Object> body = response.getBody();

            assertNotNull(body);
            assertTrue(body.containsKey("timestamp"));
            assertTrue(body.containsKey("status"));
            assertTrue(body.containsKey("error"));
            assertTrue(body.containsKey("message"));
            assertNotNull(body.get("timestamp"));
            assertNotNull(body.get("error"));
        }
    }
}