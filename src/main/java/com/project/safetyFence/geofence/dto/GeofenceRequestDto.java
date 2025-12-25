package com.project.safetyFence.geofence.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class GeofenceRequestDto {
    @NotBlank(message = "지오펜스 이름은 필수입니다")
    private String name;

    @NotBlank(message = "주소는 필수입니다")
    private String address;

    private int type;
    private String startTime;
    private String endTime;
    private String number;

    public GeofenceRequestDto(String name, String address, int type, String startTime, String endTime, String number) {
        this.name = name;
        this.address = address;
        this.type = type;
        this.startTime = startTime;
        this.endTime = endTime;
        this.number = number;
    }
}
