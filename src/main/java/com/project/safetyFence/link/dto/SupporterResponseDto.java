package com.project.safetyFence.link.dto;

import com.project.safetyFence.link.domain.Link;
import lombok.Getter;

@Getter
public class SupporterResponseDto {
    private Long linkId;
    private String supporterNumber;
    private String supporterName;
    private Boolean isPrimary;

    public SupporterResponseDto(Link link) {
        this.linkId = link.getId();
        this.supporterNumber = link.getUser().getNumber();
        this.supporterName = link.getUser().getName();
        this.isPrimary = link.getIsPrimary();
    }
}