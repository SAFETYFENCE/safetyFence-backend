package com.project.safetyFence.admin.dto;

import lombok.Getter;

@Getter
public class AdminLinkResponseDto {
    private Long id;
    private String wardNumber;       // 피보호자 전화번호 (Link의 user)
    private String wardName;         // 피보호자 이름
    private String guardianNumber;   // 보호자 전화번호 (Link의 userNumber)
    private String guardianName;     // 보호자 이름
    private String relation;         // 관계
    private Boolean isPrimary;       // 대표 보호자 여부

    public AdminLinkResponseDto(Long id, String wardNumber, String wardName,
                               String guardianNumber, String guardianName,
                               String relation, Boolean isPrimary) {
        this.id = id;
        this.wardNumber = wardNumber;
        this.wardName = wardName;
        this.guardianNumber = guardianNumber;
        this.guardianName = guardianName;
        this.relation = relation;
        this.isPrimary = isPrimary;
    }
}