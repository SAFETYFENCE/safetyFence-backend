package com.project.safetyFence.admin.dto;

import lombok.Getter;

import java.time.LocalDate;

@Getter
public class UserResponseDto {
    private String number;
    private String name;
    private LocalDate birth;
    private String linkCode;
    private int geofenceCount;
    private int medicationCount;
    private int eventCount;
    private int linkedGuardiansCount;  // 보호자인 경우: 연결된 피보호자 수
    private int linkedWardsCount;      // 피보호자인 경우: 연결된 보호자 수

    public UserResponseDto(String number, String name, LocalDate birth, String linkCode,
                          int geofenceCount, int medicationCount, int eventCount,
                          int linkedGuardiansCount, int linkedWardsCount) {
        this.number = number;
        this.name = name;
        this.birth = birth;
        this.linkCode = linkCode;
        this.geofenceCount = geofenceCount;
        this.medicationCount = medicationCount;
        this.eventCount = eventCount;
        this.linkedGuardiansCount = linkedGuardiansCount;
        this.linkedWardsCount = linkedWardsCount;
    }
}