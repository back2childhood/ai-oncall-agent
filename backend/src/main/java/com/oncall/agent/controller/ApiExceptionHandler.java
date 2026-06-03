package com.oncall.agent.controller;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validationError(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Request validation failed"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> genericError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", ex.getMessage() == null ? "Unexpected server error" : ex.getMessage()));
    }
}
