package com.project.safetyFence.medication.dto;

import lombok.Getter;

@Getter
public class MedicationRequestDto {
    private String name;
    private String dosage;
    private String purpose;
    private String frequency;
}