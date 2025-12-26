package com.project.safetyFence.medication.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MedicationCheckResponseDto {
    private String message;
    private Long medicationId;
    private LocalDateTime checkedDateTime;
}