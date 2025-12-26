package com.project.safetyFence.medication.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WardMedicationStatusDto {
    private String wardNumber;
    private String wardName;
    private List<MedicationItemDto> medications;
    private int totalMedications;
    private int checkedMedications;
}
