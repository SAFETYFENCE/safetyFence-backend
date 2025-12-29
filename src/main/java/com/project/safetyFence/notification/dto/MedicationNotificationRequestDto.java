package com.project.safetyFence.notification.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MedicationNotificationRequestDto {
    private String targetUserNumber;
    private String medicationName;
}
