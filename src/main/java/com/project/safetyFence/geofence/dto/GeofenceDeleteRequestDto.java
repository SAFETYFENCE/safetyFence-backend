package com.project.safetyFence.geofence.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GeofenceDeleteRequestDto {

    private Long id;
    private String number;

    public GeofenceDeleteRequestDto(Long id, String number) {
        this.id = id;
        this.number = number;
    }
}
