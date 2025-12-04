package com.project.safetyFence.notification.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DeviceTokenRequestDto {
    private String userNumber;
    private String token;
    private String deviceType; // "android" or "ios"
}