package com.project.safetyFence.calendar.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EventDataRequestDto {
    private String event;
    private String eventDate;
    private String startTime;
    private String number;

    public EventDataRequestDto(String event, String eventDate, String startTime, String number) {
        this.event = event;
        this.eventDate = eventDate;
        this.startTime = startTime;
        this.number = number;
    }
}
