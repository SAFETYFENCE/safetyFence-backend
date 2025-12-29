package com.project.safetyFence.link.dto;

import lombok.Getter;

@Getter
public class LinkResponseDto {
    private Long id;
    private String userNumber;
    private String relation;

    // 배터리 필드 추가
    private Integer batteryLevel;        // 0-100, null 허용
    private Long batteryLastUpdate;      // Unix timestamp (ms)

    // 기존 생성자 유지 (역호환성)
    public LinkResponseDto(Long id, String userNumber, String relation) {
        this.id = id;
        this.userNumber = userNumber;
        this.relation = relation;
        this.batteryLevel = null;
        this.batteryLastUpdate = null;
    }

    // 새 생성자 추가 (배터리 포함)
    public LinkResponseDto(Long id, String userNumber, String relation,
                          Integer batteryLevel, Long batteryLastUpdate) {
        this.id = id;
        this.userNumber = userNumber;
        this.relation = relation;
        this.batteryLevel = batteryLevel;
        this.batteryLastUpdate = batteryLastUpdate;
    }
}
