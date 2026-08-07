package com.son.soccerStreaming.global.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private Logger handlerLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        handlerLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        handlerLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void customExceptionPreservesMessageAndCause() {
        RuntimeException cause = new RuntimeException("storage failure");
        CustomException exception = new CustomException(ErrorCode.ADMIN_MEDIA_STORAGE_UNAVAILABLE, cause);

        assertThat(exception.getMessage()).isEqualTo(ErrorCode.ADMIN_MEDIA_STORAGE_UNAVAILABLE.getMessage());
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void handlesCustomExceptionWithStableErrorResponse() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleCustomException(new CustomException(ErrorCode.PLAYER_NOT_FOUND));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(response.getBody().getError()).isEqualTo(ErrorCode.PLAYER_NOT_FOUND.name());
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.PLAYER_NOT_FOUND.getMessage());
        assertThat(lastLog().getLevel()).isEqualTo(Level.WARN);
        assertThat(lastLog().getThrowableProxy()).isNull();
        assertThat(keyValue(lastLog(), "event.code")).isEqualTo(ErrorCode.PLAYER_NOT_FOUND.name());
        assertThat(keyValue(lastLog(), "http.response.status_code")).isEqualTo(404);
    }

    @Test
    void logsServerCustomExceptionAtErrorWithStackTrace() {
        CustomException exception = new CustomException(
                ErrorCode.ADMIN_MEDIA_STORAGE_UNAVAILABLE,
                new IllegalStateException("storage unavailable")
        );

        handler.handleCustomException(exception);

        assertThat(lastLog().getLevel()).isEqualTo(Level.ERROR);
        assertThat(lastLog().getThrowableProxy()).isNotNull();
        assertThat(keyValue(lastLog(), "event.code"))
                .isEqualTo(ErrorCode.ADMIN_MEDIA_STORAGE_UNAVAILABLE.name());
        assertThat(keyValue(lastLog(), "http.response.status_code")).isEqualTo(503);
    }

    @Test
    void preservesResponseStatusExceptionStatus() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid season.")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo(HttpStatus.BAD_REQUEST.name());
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid season.");
        assertThat(lastLog().getLevel()).isEqualTo(Level.WARN);
        assertThat(lastLog().getThrowableProxy()).isNull();
    }

    @Test
    void mapsOptimisticLockFailureToConflict() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleOptimisticLockException(
                new ObjectOptimisticLockingFailureException("Team", 47L)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo(ErrorCode.ADMIN_EDIT_CONFLICT.name());
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.ADMIN_EDIT_CONFLICT.getMessage());
        assertThat(lastLog().getLevel()).isEqualTo(Level.WARN);
        assertThat(lastLog().getThrowableProxy()).isNull();
    }

    @Test
    void hidesUnexpectedExceptionDetailsFromClient() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleUnexpectedException(new IllegalStateException("database password leaked"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.name());
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        assertThat(response.getBody().getMessage()).doesNotContain("database password leaked");
        assertThat(lastLog().getLevel()).isEqualTo(Level.ERROR);
        assertThat(lastLog().getThrowableProxy()).isNotNull();
    }

    @Test
    void appliesHandlersThroughSpringMvcWithoutChangingKnownClientErrors() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(handler)
                .build();

        mockMvc.perform(get("/test/status"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid season."));

        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value(ErrorCode.INTERNAL_SERVER_ERROR.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));

        logAppender.list.clear();
        mockMvc.perform(get("/test/number").param("value", "not-number"))
                .andExpect(status().isBadRequest());

        assertThat(lastLog().getLevel()).isEqualTo(Level.WARN);
        assertThat(lastLog().getFormattedMessage()).contains("MethodArgumentTypeMismatchException");
        assertThat(lastLog().getThrowableProxy()).isNull();
    }

    private ILoggingEvent lastLog() {
        assertThat(logAppender.list).isNotEmpty();
        return logAppender.list.get(logAppender.list.size() - 1);
    }

    private Object keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key))
                .map(pair -> pair.value)
                .findFirst()
                .orElse(null);
    }

    @RestController
    private static class FailingController {

        @GetMapping("/test/status")
        void status() {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid season.");
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("internal detail");
        }

        @GetMapping("/test/number")
        void number(@RequestParam Long value) {
        }
    }
}
