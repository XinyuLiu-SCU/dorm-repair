
package com.dormrepair.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数不合法");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "code", 400,
                        "message", message
                ));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String,Object>> handleStatus(org.springframework.web.server.ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("code",ex.getStatusCode().value(),"message",ex.getReason()==null?"Request failed":ex.getReason()));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String,Object>> handleMalformed(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("code",400,"message","Invalid request body"));
    }

    @ExceptionHandler({WorkflowConflictException.class, org.flowable.common.engine.api.FlowableException.class,
            org.springframework.dao.ConcurrencyFailureException.class})
    public ResponseEntity<Map<String,Object>> handleConflict(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code",409,"message","Order state or workflow conflict; reload before retrying"));
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String,Object>> handleParameter(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("code",400,"message","Invalid request parameter"));
    }
    @ExceptionHandler({org.springframework.dao.DataAccessResourceFailureException.class, org.springframework.transaction.CannotCreateTransactionException.class})
    public ResponseEntity<Map<String,Object>> handleUnavailable(Exception ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("code",503,"message","Database temporarily unavailable"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "code", 400,
                        "message", ex.getMessage()
                ));
    }

}
