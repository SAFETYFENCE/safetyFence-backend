package com.project.safetyFence.location.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatteryUpdateDto {
    private String userNumber;      // 서버에서 설정
    private Integer batteryLevel;   // 0-100
    private Long timestamp;         // Epoch milliseconds

    public BatteryUpdateDto(Integer batteryLevel) {
        this.batteryLevel = batteryLevel;
        this.timestamp = System.currentTimeMillis();
    }
}
