package com.project.safetyFence.location.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationUpdateDto {


    // 서버에서 세션으로부터 자동 설정
    private String userNumber;
    private Double latitude;
    private Double longitude;
    private Long timestamp;
    private Integer batteryLevel; // 배터리 레벨 (0-100), 선택적

    public LocationUpdateDto(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = System.currentTimeMillis();
    }

    public LocationUpdateDto(String userNumber, Double latitude, Double longitude, Long timestamp) {
        this.userNumber = userNumber;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }
}
