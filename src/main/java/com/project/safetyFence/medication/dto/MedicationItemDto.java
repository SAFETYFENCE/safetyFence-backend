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
    private boolean checkedToday;

    public MedicationItemDto(Medication medication, boolean checkedToday) {
        this.id = medication.getId();
        this.name = medication.getName();
        this.dosage = medication.getDosage();
        this.purpose = medication.getPurpose();
        this.frequency = medication.getFrequency();
        this.checkedToday = checkedToday;
    }
}
