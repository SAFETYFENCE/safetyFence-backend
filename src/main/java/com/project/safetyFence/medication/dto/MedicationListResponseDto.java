package com.project.safetyFence.medication.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class MedicationListResponseDto {
    private List<MedicationItemDto> medications;

    public MedicationListResponseDto(List<MedicationItemDto> medications) {
        this.medications = medications;
    }
}