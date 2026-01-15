package com.project.safetyFence.location.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyDistanceResponseDto {

    private String userNumber;
    private LocalDate date;
    private Double distanceMeters;  // 미터 단위
    private Double distanceKm;      // 킬로미터 단위 (소수점 2자리)
    private Integer locationCount;  // 해당 날짜의 위치 기록 수

    public DailyDistanceResponseDto(String userNumber, LocalDate date, Double distanceMeters, Integer locationCount) {
        this.userNumber = userNumber;
        this.date = date;
        this.distanceMeters = distanceMeters;
        this.distanceKm = Math.round(distanceMeters / 10.0) / 100.0; // 소수점 2자리
        this.locationCount = locationCount;
    }
}
