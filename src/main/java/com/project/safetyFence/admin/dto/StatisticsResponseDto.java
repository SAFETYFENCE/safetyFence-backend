package com.project.safetyFence.admin.dto;

import lombok.Getter;

@Getter
public class StatisticsResponseDto {
    private long totalUsers;
    private long totalLinks;
    private long totalGeofences;
    private long activeGeofences;      // 현재 활성 중인 지오펜스 (type=1 && endTime > now)
    private long totalMedications;
    private long totalEvents;
    private long totalLogs;

    public StatisticsResponseDto(long totalUsers, long totalLinks, long totalGeofences,
                                long activeGeofences, long totalMedications,
                                long totalEvents, long totalLogs) {
        this.totalUsers = totalUsers;
        this.totalLinks = totalLinks;
        this.totalGeofences = totalGeofences;
        this.activeGeofences = activeGeofences;
        this.totalMedications = totalMedications;
        this.totalEvents = totalEvents;
        this.totalLogs = totalLogs;
    }
}