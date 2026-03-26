package com.project.safetyFence.notification.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EventNotificationRequestDto {
    private String targetUserNumber;
    private String eventTitle;
    private String eventDate;
    private String eventTime;
}
