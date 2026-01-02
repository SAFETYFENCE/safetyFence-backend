package com.project.safetyFence.geofence.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GeofenceRequestDto {
    @NotBlank(message = "지오펜스 이름은 필수입니다")
    private String name;

    @NotBlank(message = "주소는 필수입니다")
    private String address;

    private int type;
    private String startDate;  // yyyy-MM-dd 형식 (일시적 지오펜스용, 선택사항)
    private String startTime;  // HH:mm 형식 (일시적 지오펜스용, 선택사항)
    private String endDate;    // yyyy-MM-dd 형식 (일시적 지오펜스용, 선택사항)
    private String endTime;    // HH:mm 형식 (일시적 지오펜스용, 선택사항)
    private String number;
}
