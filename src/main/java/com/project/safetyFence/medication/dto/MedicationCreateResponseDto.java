package com.project.safetyFence.medication.dto;

import com.project.safetyFence.medication.domain.Medication;
import lombok.Getter;

@Getter
public class MedicationCreateResponseDto {
    private String message;
    private MedicationItemDto medication;

    public MedicationCreateResponseDto(Medication medication) {
        this.message = "약이 등록되었습니다";
        this.medication = new MedicationItemDto(medication, false);
    }
}