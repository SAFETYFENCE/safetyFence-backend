package com.project.safetyFence.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenRequestDto {
    private String userNumber;
    private String token;
    private String deviceType; // "android" or "ios"
}