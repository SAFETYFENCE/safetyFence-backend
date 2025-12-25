package com.project.safetyFence.medication.exception;

import com.project.safetyFence.medication.MedicationController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice(assignableTypes = MedicationController.class)
public class MedicationControllerExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException exception) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("message", exception.getMessage());
        errorResponse.put("status", HttpStatus.BAD_REQUEST.value());

        log.warn("MedicationController - IllegalArgumentException: {}", exception.getMessage());

        return ResponseEntity.badRequest().body(errorResponse);
    }
}