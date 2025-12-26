package com.project.safetyFence.medication.dto;

import com.project.safetyFence.medication.domain.MedicationLog;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class MedicationHistoryItemDto {
    private Long logId;
    private LocalDateTime checkedDateTime;

    public MedicationHistoryItemDto(MedicationLog log) {
        this.logId = log.getId();
        this.checkedDateTime = log.getCheckedDateTime();
    }
}