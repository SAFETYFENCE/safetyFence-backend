package com.project.safetyFence.user.dto;

import lombok.Getter;

@Getter
public class DeleteAccountRequestDto {
    private String password;

    public DeleteAccountRequestDto() {
    }

    public DeleteAccountRequestDto(String password) {
        this.password = password;
    }
}