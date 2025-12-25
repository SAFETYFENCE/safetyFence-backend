package com.project.safetyFence.medication.dto;

import lombok.Getter;

@Getter
public class MedicationDeleteResponseDto {
    private String message;
    private Long deletedMedicationId;

    public MedicationDeleteResponseDto(Long id) {
        this.message = "약이 삭제되었습니다";
        this.deletedMedicationId = id;
    }
}