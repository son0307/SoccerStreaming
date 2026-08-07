package com.son.soccerStreaming.global.exception;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice   // 프로젝트 전역에서 발생하는 예외 처리
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // CustomException 발생 시 처리하는 메서드
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        logHttpException(e.getErrorCode().getStatus(), e.getErrorCode().name(), e);

        return errorResponse(
                e.getErrorCode().getStatus(),
                e.getErrorCode().name(),
                e.getErrorCode().getMessage()
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException e) {
        HttpStatusCode status = e.getStatusCode();
        HttpStatus resolvedStatus = HttpStatus.resolve(status.value());
        String error = resolvedStatus != null ? resolvedStatus.name() : "HTTP_ERROR";
        String message = e.getReason() != null ? e.getReason() : error;
        logHttpException(status, error, e);

        return errorResponse(status, error, message);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErrorResponse> handleOptimisticLockException(Exception e) {
        logHttpException(
                ErrorCode.ADMIN_EDIT_CONFLICT.getStatus(),
                ErrorCode.ADMIN_EDIT_CONFLICT.name(),
                e
        );
        return errorResponse(
                ErrorCode.ADMIN_EDIT_CONFLICT.getStatus(),
                ErrorCode.ADMIN_EDIT_CONFLICT.name(),
                ErrorCode.ADMIN_EDIT_CONFLICT.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
        logHttpException(
                ErrorCode.INTERNAL_SERVER_ERROR.getStatus(),
                ErrorCode.INTERNAL_SERVER_ERROR.name(),
                e
        );

        return errorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR.getStatus(),
                ErrorCode.INTERNAL_SERVER_ERROR.name(),
                ErrorCode.INTERNAL_SERVER_ERROR.getMessage()
        );
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        logHttpException(status, exception.getClass().getSimpleName(), exception);
        return super.handleExceptionInternal(exception, body, headers, status, request);
    }

    private void logHttpException(HttpStatusCode status, String error, Exception exception) {
        if (status.is4xxClientError()) {
            log.atWarn()
                    .addKeyValue("event.action", "http-request-rejected")
                    .addKeyValue("event.outcome", "failure")
                    .addKeyValue("event.code", error)
                    .addKeyValue("http.response.status_code", status.value())
                    .log("Client request rejected. status={}, error={}", status.value(), error);
            return;
        }

        log.atError()
                .setCause(exception)
                .addKeyValue("event.action", "http-request-failed")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("event.code", error)
                .addKeyValue("http.response.status_code", status.value())
                .log("Server request failed. status={}, error={}", status.value(), error);
    }

    private ResponseEntity<ErrorResponse> errorResponse(HttpStatusCode status, String error, String message) {
        return ResponseEntity
                .status(status)
                .body(ErrorResponse.builder()
                        .status(status.value())
                        .error(error)
                        .message(message)
                        .build());
    }

    // 클라이언트에게 전송할 에러 포맷
    @Getter
    @Builder
    public static class ErrorResponse {
        private final int status;
        private final String error;
        private final String message;
    }
}
