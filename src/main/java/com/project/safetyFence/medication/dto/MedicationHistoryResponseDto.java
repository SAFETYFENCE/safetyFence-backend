package com.project.safetyFence.medication.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MedicationHistoryResponseDto {
    private Long medicationId;
    private String medicationName;
    private List<MedicationHistoryItemDto> history;
    private int totalCheckCount;
}