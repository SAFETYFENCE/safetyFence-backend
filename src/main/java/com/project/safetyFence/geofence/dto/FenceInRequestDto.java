package com.project.safetyFence.geofence.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@ToString
public class FenceInRequestDto {
    private Long geofenceId;

    public FenceInRequestDto(Long geofenceId) {
        this.geofenceId = geofenceId;
    }
}
