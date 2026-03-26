package com.project.safetyFence.medication.dto;

import lombok.Getter;

@Getter
public class MedicationRequestDto {
    private String name;
    private String dosage;
    private String purpose;
    private String frequency;
    private String targetUserNumber;  // 약을 추가할 대상 (없으면 본인)
}