package com.project.safetyFence.medication.dto;

import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class MedicationListResponseDto {
    private LocalDate checkDate;
    private List<MedicationItemDto> medications;

    public MedicationListResponseDto(LocalDate checkDate, List<MedicationItemDto> medications) {
        this.checkDate = checkDate;
        this.medications = medications;
    }
}