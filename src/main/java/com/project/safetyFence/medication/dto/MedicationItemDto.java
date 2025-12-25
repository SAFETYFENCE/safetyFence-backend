package com.project.safetyFence.medication.dto;

import com.project.safetyFence.medication.domain.Medication;
import lombok.Getter;

@Getter
public class MedicationItemDto {
    private Long id;
    private String name;
    private String dosage;
    private String purpose;
    private String frequency;

    public MedicationItemDto(Medication medication) {
        this.id = medication.getId();
        this.name = medication.getName();
        this.dosage = medication.getDosage();
        this.purpose = medication.getPurpose();
        this.frequency = medication.getFrequency();
    }
}
